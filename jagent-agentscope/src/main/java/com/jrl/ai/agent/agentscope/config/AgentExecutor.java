package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.CompositeScorer;
import com.jrl.ai.agent.core.evaluation.EvaluationContext;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.evaluation.OptimizationReport;
import com.jrl.ai.agent.core.evaluation.OptimizationReportStore;
import com.jrl.ai.agent.core.evaluation.Evaluator;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.AgentResponse;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Agent 通用执行器 — 统一入口，同步/流式双通道，评测为链路内置步骤。
 *
 * <p>同步链路：{@code execute()} → Agent.execute() → 拦截器链（含评测拦截器）
 * <p>流式链路：{@code stream()} → harness.streamEvents() → doFinally 自动触发评测
 *
 * <p>业务层只需关心输入输出，trace/评测/优化建议全部自动处理。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 同步：业务只关心返回值
 * AgentResponse<List<TagInfo>> response = agentExecutor.execute(
 *     "tagger", input, context,
 *     taskResult -> parseTags(taskResult)
 * );
 *
 * // 流式：返回文本增量流，评测自动处理
 * Flux<String> stream = agentExecutor.stream("chat", "你好", sessionId, userId);
 * }</pre>
 */
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    private final AgentFactory agentFactory;
    private final EvaluationStore evaluationStore;
    private final OptimizationReportStore optimizationReportStore;
    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;

    /**
     * 创建 AgentExecutor（完整版）。
     *
     * @param agentFactory            Agent 工厂
     * @param evaluationStore         评测结果存储（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     * @param evaluators              评测器列表（流式链路用，可选）
     * @param compositeScorer         复合评分器（流式链路用，可选）
     */
    public AgentExecutor(AgentFactory agentFactory,
                         EvaluationStore evaluationStore,
                         OptimizationReportStore optimizationReportStore,
                         List<Evaluator> evaluators,
                         CompositeScorer compositeScorer) {
        this.agentFactory = agentFactory;
        this.evaluationStore = evaluationStore;
        this.optimizationReportStore = optimizationReportStore;
        this.evaluators = evaluators != null ? List.copyOf(evaluators) : List.of();
        this.compositeScorer = compositeScorer;
        log.info("[AgentExecutor] Initialized: evaluationStore={}, optimizationReportStore={}, evaluators={}",
                evaluationStore != null, optimizationReportStore != null, this.evaluators.size());
    }

    /**
     * 创建 AgentExecutor（简化版 — 无流式评测能力）。
     *
     * @param agentFactory            Agent 工厂
     * @param evaluationStore         评测结果存储（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     */
    public AgentExecutor(AgentFactory agentFactory,
                         EvaluationStore evaluationStore,
                         OptimizationReportStore optimizationReportStore) {
        this(agentFactory, evaluationStore, optimizationReportStore, List.of(), null);
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
                    traceBuilder.step(s.name(), s.duration(), s.detail());
                }
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

    // ==================== 流式通道 ====================

    /**
     * 流式执行 Agent — 返回文本增量流，流结束后自动触发评测。
     *
     * <p>评测是链路内置步骤，与同步链路一样自动执行，调用方无需关心。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 文本增量流
     */
    public Flux<String> stream(String agentKey, String text, String sessionId, String userId) {
        StringBuilder outputCollector = new StringBuilder();

        return agentFactory.getHarnessAgent(agentKey)
                .streamEvents(new UserMessage(text))
                .filter(event -> event.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .map(event -> {
                    if (event instanceof TextBlockDeltaEvent delta) {
                        return delta.getDelta();
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .doOnNext(outputCollector::append)
                .doFinally(signal -> {
                    String output = outputCollector.toString();
                    if (!output.isEmpty()) {
                        runStreamEvaluation(agentKey, sessionId, text, output);
                    }
                });
    }

    /**
     * 流式链路内置评测步骤 — 收集完整输出后执行评测并持久化。
     *
     * <p>遍历所有已注册的 Evaluator，合并评分，计算 compositeScore，保存到 EvaluationStore。
     */
    private void runStreamEvaluation(String agentKey, String sessionId, String input, String output) {
        if (evaluationStore == null || evaluators.isEmpty() || compositeScorer == null) {
            log.debug("[AgentExecutor] 流式评测跳过: evaluationStore={}, evaluators={}, compositeScorer={}",
                    evaluationStore != null, evaluators.size(), compositeScorer != null);
            return;
        }

        try {
            Agent agent = agentFactory.getAgent(agentKey);
            EvaluationContext context = EvaluationContext.of(agent.id(), input, output, null);

            // 遍历所有评测器，合并评分
            Map<com.jrl.ai.agent.core.evaluation.EvaluationDimension,
                    com.jrl.ai.agent.core.evaluation.DimensionScore> allScores =
                    new EnumMap<>(com.jrl.ai.agent.core.evaluation.EvaluationDimension.class);

            for (Evaluator evaluator : evaluators) {
                try {
                    EvaluationResult evalResult = evaluator.evaluate(context);
                    allScores.putAll(evalResult.scores());
                } catch (Exception e) {
                    log.warn("[AgentExecutor] Evaluator {} failed: {}",
                            evaluator.getClass().getSimpleName(), e.getMessage());
                }
            }

            double compositeScore = compositeScorer.compute(allScores);

            EvaluationResult finalResult = EvaluationResult.builder(agent.id())
                    .sessionId(sessionId)
                    .scores(allScores)
                    .compositeScore(compositeScore)
                    .input(input)
                    .output(output)
                    .build();

            evaluationStore.save(finalResult);
            log.info("[AgentExecutor] 流式评测完成: agent={} session={} score={}",
                    agentKey, sessionId, String.format("%.2f", compositeScore));
        } catch (Exception e) {
            log.warn("[AgentExecutor] 流式评测失败: {}", e.getMessage());
        }
    }

    // ==================== 公共辅助 ====================

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
