package com.jrl.ai.agent.demo.service;

import com.jrl.ai.agent.agentscope.config.AgentExecutor;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.CompositeScorer;
import com.jrl.ai.agent.core.evaluation.EvaluationContext;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.evaluation.Evaluator;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 业务服务 — 业务编排层，流式场景自行处理评测。
 *
 * <p>同步链路：AgentExecutor → 拦截器链自动生效（含评测）
 * <p>流式链路：绕过拦截器，本服务在 doFinally 中触发评测
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentExecutor agentExecutor;
    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;
    private final EvaluationStore evaluationStore;

    public AgentService(AgentExecutor agentExecutor,
                        List<Evaluator> evaluators,
                        CompositeScorer compositeScorer,
                        EvaluationStore evaluationStore) {
        this.agentExecutor = agentExecutor;
        this.evaluators = evaluators;
        this.compositeScorer = compositeScorer;
        this.evaluationStore = evaluationStore;
    }

    /**
     * 同步对话 — 返回 AgentResponse，业务数据为响应文本。
     *
     * <p>评测由拦截器链自动处理。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return AgentResponse，data 为 Agent 响应文本
     */
    public AgentResponse<String> chat(String agentKey, String text, String sessionId, String userId) {
        AgentContext context = AgentContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        return agentExecutor.execute(
                agentKey,
                ChatMessage.user(text),
                context,
                taskResult -> (String) taskResult.result().getOrDefault("response", "")
        );
    }

    /**
     * 流式对话 — 返回文本增量流，流结束后触发评测。
     *
     * <p>流式链路绕过拦截器，本服务在 doFinally 中执行评测。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 文本增量流
     */
    public Flux<String> stream(String agentKey, String text, String sessionId, String userId) {
        StringBuilder outputCollector = new StringBuilder();

        return agentExecutor.stream(agentKey, text, sessionId, userId)
                .doOnNext(outputCollector::append)
                .doFinally(signal -> {
                    String output = outputCollector.toString();
                    if (!output.isEmpty()) {
                        runStreamEvaluation(agentKey, sessionId, text, output);
                    }
                });
    }

    /**
     * 流式链路评测 — 收集完整输出后执行评测并持久化。
     */
    private void runStreamEvaluation(String agentKey, String sessionId, String input, String output) {
        if (evaluationStore == null || evaluators.isEmpty() || compositeScorer == null) {
            return;
        }

        try {
            Agent agent = agentExecutor.getAgentFactory().getAgent(agentKey);
            EvaluationContext context = EvaluationContext.of(agent.id(), input, output, null);

            Map<com.jrl.ai.agent.core.evaluation.EvaluationDimension,
                    com.jrl.ai.agent.core.evaluation.DimensionScore> allScores =
                    new EnumMap<>(com.jrl.ai.agent.core.evaluation.EvaluationDimension.class);

            for (Evaluator evaluator : evaluators) {
                try {
                    EvaluationResult evalResult = evaluator.evaluate(context);
                    allScores.putAll(evalResult.scores());
                } catch (Exception e) {
                    log.warn("[AgentService] Evaluator {} failed: {}",
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
            log.info("[AgentService] 流式评测完成: agent={} session={} score={}",
                    agentKey, sessionId, String.format("%.2f", compositeScore));
        } catch (Exception e) {
            log.warn("[AgentService] 流式评测失败: {}", e.getMessage());
        }
    }

    /**
     * 列出所有已注册的 Agent 信息。
     *
     * @return Agent 标识 → 名称映射
     */
    public Map<String, String> listAgents() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : agentExecutor.getAgentFactory().allAgentKeys()) {
            Agent agent = agentExecutor.getAgentFactory().getAgent(key);
            result.put(key, agent.name());
        }
        return result;
    }
}
