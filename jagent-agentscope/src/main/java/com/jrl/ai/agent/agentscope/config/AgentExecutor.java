package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.evaluation.OptimizationReport;
import com.jrl.ai.agent.core.evaluation.OptimizationReportStore;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.AgentResponse;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.function.Function;

/**
 * Agent 通用执行器 — 纯执行引擎，同步/责任链双通道。
 *
 * <p>评测由拦截器（AOP）自动处理，AgentExecutor 不关心评测逻辑，只负责执行和查询结果。
 *
 * <ul>
 *   <li>同步链路：{@code execute()} → Agent.execute() → 拦截器链自动生效</li>
 *   <li>责任链：{@code executeChain()} → 多个 Agent 顺序执行，每个都走拦截器</li>
 * </ul>
 *
 * <p>用法示例：
 * <pre>{@code
 * // 同步执行
 * AgentResponse<String> response = agentExecutor.execute(
 *     "chat", input, context,
 *     taskResult -> (String) taskResult.result().get("response")
 * );
 *
 * // 责任链执行
 * TaskResult result = agentExecutor.executeChain(
 *     List.of("chat", "summarizer"), input, context
 * );
 * }</pre>
 */
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    private final AgentFactory agentFactory;
    private final EvaluationStore evaluationStore;
    private final OptimizationReportStore optimizationReportStore;

    /**
     * 创建 AgentExecutor。
     *
     * @param agentFactory            Agent 工厂
     * @param evaluationStore         评测结果存储（可选，用于查询结果）
     * @param optimizationReportStore 优化报告存储（可选，用于查询结果）
     */
    public AgentExecutor(AgentFactory agentFactory,
                         EvaluationStore evaluationStore,
                         OptimizationReportStore optimizationReportStore) {
        this.agentFactory = agentFactory;
        this.evaluationStore = evaluationStore;
        this.optimizationReportStore = optimizationReportStore;
        log.info("[AgentExecutor] Initialized: evaluationStore={}, optimizationReportStore={}",
                evaluationStore != null, optimizationReportStore != null);
    }

    // ==================== 同步通道 ====================

    /**
     * 业务结果映射器 — 从 TaskResult 提取业务数据，同时可向 traceBuilder 追加业务步骤。
     *
     * @param <T> 业务数据类型
     */
    @FunctionalInterface
    public interface ResultMapper<T> {
        /**
         * 从 Agent 执行结果中提取业务数据。
         *
         * @param taskResult   Agent 执行结果
         * @param traceBuilder 业务层可追加自己的执行步骤到 trace
         * @return 业务数据
         */
        T map(TaskResult taskResult, ExecutionTrace.Builder traceBuilder);
    }

    /**
     * 同步执行 Agent 并返回标准化响应（简单版）。
     *
     * @param agentKey Agent 配置键名
     * @param input    用户输入
     * @param context  运行时上下文
     * @param mapper   业务数据提取函数
     * @param <T>      业务数据类型
     * @return 标准化响应
     */
    public <T> AgentResponse<T> execute(String agentKey, ChatMessage input,
                                         AgentContext context, Function<TaskResult, T> mapper) {
        return execute(agentKey, input, context, (taskResult, traceBuilder) -> mapper.apply(taskResult));
    }

    /**
     * 同步执行 Agent 并返回标准化响应（完整版 — 可追加业务 trace 步骤）。
     *
     * <p>评测由拦截器链自动处理，无需手动触发。
     *
     * @param agentKey Agent 配置键名
     * @param input    用户输入
     * @param context  运行时上下文
     * @param mapper   业务结果映射器
     * @param <T>      业务数据类型
     * @return 标准化响应
     */
    public <T> AgentResponse<T> execute(String agentKey, ChatMessage input,
                                         AgentContext context, ResultMapper<T> mapper) {
        long start = System.currentTimeMillis();
        ExecutionTrace.Builder traceBuilder = ExecutionTrace.builder().start();

        try {
            Agent agent = agentFactory.getAgent(agentKey);
            TaskResult taskResult = agent.execute(input, context);

            if (!taskResult.isSuccess()) {
                String errorMsg = taskResult.error() != null ? taskResult.error().getMessage() : "未知错误";
                log.error("[AgentExecutor] agent={} failed: {}", agentKey, errorMsg);
                return AgentResponse.failure(errorMsg, traceBuilder.build(), System.currentTimeMillis() - start);
            }

            if (taskResult.trace() != null) {
                for (ExecutionTrace.Step s : taskResult.trace().steps()) {
                    traceBuilder.step(s);
                }
                // OTel 链路快照跟随外层 trace 一次返回
                traceBuilder.otel(taskResult.trace().otel());
            }

            T businessData = mapper.map(taskResult, traceBuilder);
            long processTime = System.currentTimeMillis() - start;
            ExecutionTrace trace = traceBuilder.build();

            log.info("[AgentExecutor] agent={} completed, processTime={}ms", agentKey, processTime);

            AgentResponse<T> response = AgentResponse.success(businessData, taskResult.usage(), trace, processTime);
            return enrichWithEvaluation(response, agent.id());

        } catch (Exception e) {
            log.error("[AgentExecutor] agent={} unexpected error", agentKey, e);
            long processTime = System.currentTimeMillis() - start;
            return AgentResponse.failure(e.getMessage(), traceBuilder.build(), processTime);
        }
    }

    // ==================== 责任链执行 ====================

    /**
     * 执行 Agent 责任链 — 按顺序执行多个 Agent，前一个的输出通过上下文传递给下一个。
     *
     * <p>用于业务 Agent 编排，如：{@code [ChatAgent, SummarizerAgent]}
     * <p>每个 Agent 执行时都走拦截器链（含评测）。
     *
     * @param agentKeys Agent 配置键名列表（按执行顺序）
     * @param input     用户输入
     * @param context   运行时上下文
     * @return 最后一个 Agent 的 TaskResult
     */
    public TaskResult executeChain(List<String> agentKeys, ChatMessage input, AgentContext context) {
        if (agentKeys == null || agentKeys.isEmpty()) {
            throw new IllegalArgumentException("agentKeys cannot be empty");
        }

        long start = System.currentTimeMillis();
        TaskResult lastResult = null;

        for (int i = 0; i < agentKeys.size(); i++) {
            String agentKey = agentKeys.get(i);

            try {
                Agent agent = agentFactory.getAgent(agentKey);
                log.debug("[AgentExecutor] Chain step {}/{}: executing agent={}",
                        i + 1, agentKeys.size(), agentKey);

                TaskResult result = agent.execute(input, context);

                if (!result.isSuccess()) {
                    log.error("[AgentExecutor] Chain step {} failed: agent={}, error={}",
                            i + 1, agentKey, result.error());
                    return result;
                }

                // 将当前 Agent 的输出写入上下文，供下一个 Agent 使用
                String response = (String) result.result().getOrDefault("response", "");
                context.put("chain.previousOutput", response);
                context.put("chain.previousAgentId", agent.id());

                lastResult = result;
                log.debug("[AgentExecutor] Chain step {} completed: agent={}", i + 1, agentKey);

            } catch (Exception e) {
                log.error("[AgentExecutor] Chain step {} unexpected error: agent={}", i + 1, agentKey, e);
                return TaskResult.failure(agentKey, context.sessionId(),
                        "CHAIN_ERROR", e.getMessage(), System.currentTimeMillis() - start);
            }
        }

        return lastResult;
    }

    /**
     * 执行 Agent 责任链并提取业务数据。
     *
     * @param agentKeys Agent 配置键名列表
     * @param input     用户输入
     * @param context   运行时上下文
     * @param mapper    从最后一个 Agent 的 TaskResult 提取业务数据
     * @param <T>       业务数据类型
     * @return 标准化响应
     */
    public <T> AgentResponse<T> executeChain(List<String> agentKeys, ChatMessage input,
                                              AgentContext context, Function<TaskResult, T> mapper) {
        long start = System.currentTimeMillis();
        ExecutionTrace.Builder traceBuilder = ExecutionTrace.builder().start();

        try {
            TaskResult chainResult = executeChain(agentKeys, input, context);

            if (!chainResult.isSuccess()) {
                String errorMsg = chainResult.error() != null ? chainResult.error().getMessage() : "链执行失败";
                return AgentResponse.failure(errorMsg, traceBuilder.build(), System.currentTimeMillis() - start);
            }

            T businessData = mapper.apply(chainResult);
            long processTime = System.currentTimeMillis() - start;

            log.info("[AgentExecutor] Chain completed: agents={}, processTime={}ms", agentKeys, processTime);

            return AgentResponse.success(businessData, chainResult.usage(), traceBuilder.build(), processTime);

        } catch (Exception e) {
            log.error("[AgentExecutor] Chain unexpected error: agents={}", agentKeys, e);
            return AgentResponse.failure(e.getMessage(), traceBuilder.build(), System.currentTimeMillis() - start);
        }
    }

    // ==================== 公共辅助 ====================

    /**
     * 自动查询评测结果和优化建议，追加到响应中。
     *
     * <p>响应中的评测仅包含评分数据（剥离内部 trace）——
     * 链路信息统一由响应外层的 {@code trace} 字段承载。
     */
    private <T> AgentResponse<T> enrichWithEvaluation(AgentResponse<T> response, String agentId) {
        if (evaluationStore == null) {
            return response;
        }

        List<EvaluationResult> results = evaluationStore.findByAgent(agentId, 1);
        if (results.isEmpty()) {
            return response;
        }

        EvaluationResult evaluation = results.getFirst().withoutTrace();
        response = response.withEvaluation(evaluation);

        if (optimizationReportStore != null) {
            List<OptimizationReport> reports = optimizationReportStore.findByAgent(agentId, 1);
            if (!reports.isEmpty()) {
                response = response.withOptimization(reports.getFirst());
            }
        }

        return response;
    }

    /**
     * 获取 Agent 工厂（供业务层列出 Agent 等场景使用）。
     */
    public AgentFactory getAgentFactory() {
        return agentFactory;
    }
}
