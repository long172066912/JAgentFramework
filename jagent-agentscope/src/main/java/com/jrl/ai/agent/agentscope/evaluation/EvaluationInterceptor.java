package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 评测拦截器 — Agent 执行完成后自动触发评测链。
 *
 * <p>通过配置 {@code jagent.evaluation.enabled=true} 启用，
 * 自动收集所有已注册的 {@link Evaluator} Bean 并依次执行评测。
 */
public class EvaluationInterceptor implements AgentInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EvaluationInterceptor.class);

    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;
    private final EvaluationStore store;

    /**
     * 创建评测拦截器。
     *
     * @param evaluators       所有已注册的评测器
     * @param compositeScorer  复合评分器
     * @param store            评测结果存储
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store) {
        this.evaluators = evaluators;
        this.compositeScorer = compositeScorer;
        this.store = store;
    }

    @Override
    public void afterExecute(Agent agent, ChatMessage input, AgentContext context, TaskResult result) {
        try {
            // 构建评测上下文
            Map<String, Object> taskResultMap = Map.of(
                    "status", result.status().name(),
                    "durationMs", result.durationMs()
            );

            EvaluationContext evalContext = new EvaluationContext(
                    agent.id(),
                    result.sessionId(),
                    input != null ? input.content() : null,
                    result.result() != null ? String.valueOf(result.result()) : null,
                    result.trace(),
                    taskResultMap,
                    Map.of()
            );

            // 遍历所有评测器，合并评分
            Map<EvaluationDimension, DimensionScore> allScores = new EnumMap<>(EvaluationDimension.class);

            for (Evaluator evaluator : evaluators) {
                try {
                    EvaluationResult evalResult = evaluator.evaluate(evalContext);
                    allScores.putAll(evalResult.scores());
                } catch (Exception e) {
                    log.warn("[Evaluation] Evaluator {} failed: {}",
                            evaluator.getClass().getSimpleName(), e.getMessage());
                }
            }

            // 计算加权总分
            double compositeScore = compositeScorer.compute(allScores);

            // 构建最终评测结果
            EvaluationResult finalResult = EvaluationResult.builder(agent.id())
                    .sessionId(result.sessionId())
                    .scores(allScores)
                    .compositeScore(compositeScore)
                    .trace(result.trace())
                    .input(input != null ? input.content() : null)
                    .output(result.result() != null ? String.valueOf(result.result()) : null)
                    .build();

            // 持久化
            store.save(finalResult);

            log.info("[Evaluation] agent={} composite={:.2f} dims={}",
                    agent.id(), compositeScore, allScores.size());

        } catch (Exception e) {
            log.error("[Evaluation] Failed to evaluate agent={}: {}", agent.id(), e.getMessage(), e);
        }
    }
}
