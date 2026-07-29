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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Agent 通用执行器 — 封装 Agent 调用 + 公共字段自动采集 + 业务数据提取。
 *
 * <p>业务层只需提供「如何从 TaskResult 中提取业务数据」的逻辑，
 * 框架自动处理 trace 采集、tokenUsage 统计、评测查询、优化建议查询。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 简单用法：业务只关心返回值
 * AgentResponse<List<TagInfo>> response = agentExecutor.execute(
 *     "tagger", input, context,
 *     taskResult -> parseTags(taskResult)
 * );
 *
 * // 带额外 trace 步骤：业务在提取数据的同时记录自己的执行步骤
 * AgentResponse<List<TagInfo>> response = agentExecutor.execute(
 *     "tagger", input, context,
 *     (taskResult, traceBuilder) -> {
 *         long start = System.currentTimeMillis();
 *         List<TagInfo> tags = parseTags(taskResult);
 *         traceBuilder.step("PARSE_TAGS", System.currentTimeMillis() - start, "parsed=%d".formatted(tags.size()));
 *         return tags;
 *     }
 * );
 *
 * // 响应中自动包含公共字段
 * response.data();         // 业务数据
 * response.trace();        // Agent trace + 业务 trace 自动合并
 * response.tokenUsage();   // 自动采集
 * response.evaluation();   // 自动查询
 * response.optimization(); // 自动查询
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
     * @param evaluationStore         评测结果存储（可选）
     * @param optimizationReportStore 优化报告存储（可选）
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
     * 执行 Agent 并返回标准化响应（简单版 — 不需要追加业务 trace 步骤）。
     *
     * @param agentKey Agent 配置键名（对应 application.yml 中的 key）
     * @param input    用户输入
     * @param context  运行时上下文
     * @param mapper   业务数据提取函数
     * @param <T>      业务数据类型
     * @return 包含业务数据 + 公共字段的标准化响应
     */
    public <T> AgentResponse<T> execute(String agentKey, ChatMessage input,
                                         AgentContext context, Function<TaskResult, T> mapper) {
        return execute(agentKey, input, context, (taskResult, traceBuilder) -> mapper.apply(taskResult));
    }

    /**
     * 执行 Agent 并返回标准化响应（完整版 — 可追加业务 trace 步骤）。
     *
     * <p>执行流程：
     * <ol>
     *   <li>通过 agentFactory 获取 Agent 实例</li>
     *   <li>执行 Agent（拦截器链自动生效：Metrics → Evaluation → Skill 评分）</li>
     *   <li>调用业务 mapper 提取业务数据（业务可追加 trace 步骤）</li>
     *   <li>合并 Agent trace + 业务 trace</li>
     *   <li>自动查询评测结果 + 优化建议</li>
     *   <li>封装为 AgentResponse 返回</li>
     * </ol>
     *
     * @param agentKey Agent 配置键名
     * @param input    用户输入
     * @param context  运行时上下文
     * @param mapper   业务结果映射器（可追加 trace 步骤）
     * @param <T>      业务数据类型
     * @return 包含业务数据 + 公共字段的标准化响应
     */
    public <T> AgentResponse<T> execute(String agentKey, ChatMessage input,
                                         AgentContext context, ResultMapper<T> mapper) {
        long start = System.currentTimeMillis();
        ExecutionTrace.Builder traceBuilder = ExecutionTrace.builder().start();

        try {
            // 1. 获取 Agent 并执行
            Agent agent = agentFactory.getAgent(agentKey);
            TaskResult taskResult = agent.execute(input, context);

            if (!taskResult.isSuccess()) {
                String errorMsg = taskResult.error() != null ? taskResult.error().getMessage() : "未知错误";
                log.error("[AgentExecutor] agent={} failed: {}", agentKey, errorMsg);
                return AgentResponse.failure(errorMsg, traceBuilder.build(), System.currentTimeMillis() - start);
            }

            // 2. 合并 Agent 层 trace 步骤
            if (taskResult.trace() != null) {
                for (ExecutionTrace.Step s : taskResult.trace().steps()) {
                    traceBuilder.step(s.name(), s.duration(), s.detail());
                }
            }

            // 3. 调用业务 mapper（业务可追加自己的 trace 步骤）
            T businessData = mapper.map(taskResult, traceBuilder);

            long processTime = System.currentTimeMillis() - start;
            ExecutionTrace trace = traceBuilder.build();

            log.info("[AgentExecutor] agent={} completed, processTime={}ms", agentKey, processTime);

            // 4. 构建基础响应
            AgentResponse<T> response = AgentResponse.success(
                    businessData, taskResult.usage(), trace, processTime);

            // 5. 自动查询评测 + 优化建议
            return enrichWithEvaluation(response, agent.id());

        } catch (Exception e) {
            log.error("[AgentExecutor] agent={} unexpected error", agentKey, e);
            long processTime = System.currentTimeMillis() - start;
            return AgentResponse.failure(e.getMessage(), traceBuilder.build(), processTime);
        }
    }

    /**
     * 自动查询评测结果和优化建议，追加到响应中。
     */
    private <T> AgentResponse<T> enrichWithEvaluation(AgentResponse<T> response, String agentId) {
        if (evaluationStore == null) {
            return response;
        }

        List<EvaluationResult> results = evaluationStore.findByAgent(agentId, 1);
        if (results.isEmpty()) {
            return response;
        }

        EvaluationResult evaluation = results.getFirst();
        response = response.withEvaluation(evaluation);

        // 查询优化建议
        if (optimizationReportStore != null) {
            List<OptimizationReport> reports = optimizationReportStore.findByAgent(agentId, 1);
            if (!reports.isEmpty()) {
                response = response.withOptimization(reports.getFirst());
            }
        }

        return response;
    }
}
