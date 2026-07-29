package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.agentscope.agent.StreamAgentInterceptor;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.io.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 流式评测拦截器 — 在流式执行完成后自动触发评测。
 *
 * <p>实现 {@link StreamAgentInterceptor}，在流结束后收集完整输出并执行评测。
 * 这是评测系统对流式链路的 AOP 包装，与同步链路的 {@link EvaluationInterceptor} 对应。
 */
public class StreamEvaluationInterceptor implements StreamAgentInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StreamEvaluationInterceptor.class);

    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;
    private final EvaluationStore evaluationStore;

    /**
     * 创建流式评测拦截器。
     *
     * @param evaluators      评测器列表
     * @param compositeScorer 复合评分器
     * @param evaluationStore 评测结果存储
     */
    public StreamEvaluationInterceptor(List<Evaluator> evaluators,
                                        CompositeScorer compositeScorer,
                                        EvaluationStore evaluationStore) {
        this.evaluators = evaluators != null ? List.copyOf(evaluators) : List.of();
        this.compositeScorer = compositeScorer;
        this.evaluationStore = evaluationStore;
        log.info("[StreamEvaluationInterceptor] Initialized with {} evaluators", this.evaluators.size());
    }

    @Override
    public Flux<String> aroundStream(Agent agent, ChatMessage input, AgentContext context,
                                      StreamExecutionChain chain) {
        StringBuilder outputCollector = new StringBuilder();

        return chain.proceed(input, context)
                .doOnNext(outputCollector::append)
                .doFinally(signal -> {
                    String output = outputCollector.toString();
                    if (!output.isEmpty()) {
                        onStreamComplete(agent, input, context, output);
                    }
                });
    }

    @Override
    public void onStreamComplete(Agent agent, ChatMessage input, AgentContext context, String fullOutput) {
        if (evaluationStore == null || evaluators.isEmpty() || compositeScorer == null) {
            return;
        }

        try {
            EvaluationContext evalContext = EvaluationContext.of(
                    agent.id(), input.content(), fullOutput, null);

            Map<EvaluationDimension, DimensionScore> allScores =
                    new EnumMap<>(EvaluationDimension.class);

            for (Evaluator evaluator : evaluators) {
                try {
                    EvaluationResult evalResult = evaluator.evaluate(evalContext);
                    allScores.putAll(evalResult.scores());
                } catch (Exception e) {
                    log.warn("[StreamEvaluationInterceptor] Evaluator {} failed: {}",
                            evaluator.getClass().getSimpleName(), e.getMessage());
                }
            }

            double compositeScore = compositeScorer.compute(allScores);

            EvaluationResult finalResult = EvaluationResult.builder(agent.id())
                    .sessionId(context.sessionId())
                    .scores(allScores)
                    .compositeScore(compositeScore)
                    .input(input.content())
                    .output(fullOutput)
                    .build();

            evaluationStore.save(finalResult);
            log.info("[StreamEvaluationInterceptor] 流式评测完成: agent={} session={} score={}",
                    agent.id(), context.sessionId(), String.format("%.2f", compositeScore));
        } catch (Exception e) {
            log.warn("[StreamEvaluationInterceptor] 流式评测失败: {}", e.getMessage());
        }
    }
}
