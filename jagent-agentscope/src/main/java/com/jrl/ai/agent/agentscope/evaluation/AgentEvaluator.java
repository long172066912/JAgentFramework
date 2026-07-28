package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.io.MessageRole;
import com.jrl.ai.agent.core.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Agent 评测器 — 将任意 Agent 包装为 Evaluator。
 *
 * <p>用户可用自己的 Agent 做评测，只需将其包装为 AgentEvaluator：
 * <pre>{@code
 * @Bean
 * public Evaluator myAgentEvaluator(Agent myJudgeAgent) {
 *     return new AgentEvaluator(myJudgeAgent, EvaluationDimension.INTELLIGENCE);
 * }
 * }</pre>
 *
 * <p>评测 Agent 的输入格式：
 * <pre>
 * 请评测以下 AI 输出：
 * 用户输入：{input}
 * AI 输出：{output}
 * 请给出 0.0 到 1.0 的评分，仅回复数字。
 * </pre>
 */
public class AgentEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluator.class);

    private final Agent judgeAgent;
    private final Set<EvaluationDimension> dimensions;
    private final EvaluationLevel level;

    /**
     * 创建 Agent 评测器。
     *
     * @param judgeAgent 评测用 Agent
     * @param dimensions 该 Agent 负责的评测维度
     */
    public AgentEvaluator(Agent judgeAgent, EvaluationDimension... dimensions) {
        this(judgeAgent, Set.of(dimensions), EvaluationLevel.LLM_JUDGE);
    }

    /**
     * 创建 Agent 评测器（指定层级）。
     *
     * @param judgeAgent 评测用 Agent
     * @param dimensions 该 Agent 负责的评测维度
     * @param level      评测层级
     */
    public AgentEvaluator(Agent judgeAgent, Set<EvaluationDimension> dimensions, EvaluationLevel level) {
        this.judgeAgent = judgeAgent;
        this.dimensions = dimensions;
        this.level = level;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Map<EvaluationDimension, DimensionScore> scores = new EnumMap<>(EvaluationDimension.class);

        try {
            String prompt = buildEvalPrompt(context);
            ChatMessage input = ChatMessage.of(MessageRole.USER, prompt);
            AgentContext evalCtx = AgentContext.builder().sessionId("eval-" + context.agentId()).build();
            TaskResult result = judgeAgent.execute(input, evalCtx);

            double score = parseScore(result);

            for (EvaluationDimension dim : dimensions) {
                scores.put(dim, DimensionScore.of(dim, score, level,
                        "Agent[" + judgeAgent.id() + "] 评测"));
            }

            log.debug("[Evaluation] AgentEvaluator agent={} judge={} score={}",
                    context.agentId(), judgeAgent.id(), score);

        } catch (Exception e) {
            log.warn("[Evaluation] AgentEvaluator failed: agent={} judge={}: {}",
                    context.agentId(), judgeAgent.id(), e.getMessage());

            for (EvaluationDimension dim : dimensions) {
                scores.put(dim, DimensionScore.of(dim, 0.5, level,
                        "评测Agent执行失败: " + e.getMessage()));
            }
        }

        return EvaluationResult.builder(context.agentId())
                .sessionId(context.sessionId())
                .scores(scores)
                .trace(context.trace())
                .input(context.input())
                .output(context.output())
                .build();
    }

    @Override
    public Set<EvaluationDimension> supportedDimensions() {
        return dimensions;
    }

    @Override
    public EvaluationLevel level() {
        return level;
    }

    private String buildEvalPrompt(EvaluationContext context) {
        return String.format("""
                请评测以下 AI 输出质量。
                
                用户输入：%s
                
                AI 输出：%s
                
                请给出 0.0 到 1.0 的评分，仅回复数字。
                """,
                context.input() != null ? context.input() : "(无)",
                context.output() != null ? context.output() : "(无)");
    }

    private double parseScore(TaskResult result) {
        if (result == null || !result.isSuccess() || result.result() == null) {
            return 0.5;
        }

        Object content = result.result().get("content");
        if (content == null) {
            content = result.result().get("text");
        }
        if (content == null) {
            return 0.5;
        }

        try {
            String text = String.valueOf(content).trim();
            // 尝试直接解析为数字
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(text)));
        } catch (NumberFormatException e) {
            // 尝试从文本中提取数字
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)").matcher(String.valueOf(content));
            if (matcher.find()) {
                return Math.max(0.0, Math.min(1.0, Double.parseDouble(matcher.group(1))));
            }
            return 0.5;
        }
    }
}
