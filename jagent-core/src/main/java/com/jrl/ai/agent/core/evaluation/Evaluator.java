package com.jrl.ai.agent.core.evaluation;

import java.util.Map;
import java.util.Set;

/**
 * 评测器 — 核心评测接口。
 *
 * <p>实现此接口可自定义评测逻辑，注册为 Spring Bean 后自动加入评测链。
 *
 * <p>框架内置实现：
 * <ul>
 *   <li>{@code RuleBasedEvaluator} — Tier1 零成本规则评测</li>
 *   <li>{@code LLMJudgeEvaluator} — Tier2 LLM 语义评测</li>
 *   <li>{@code AgentEvaluator} — 将任意 Agent 包装为评测器</li>
 * </ul>
 *
 * <p>用户自定义示例：
 * <pre>{@code
 * @Component
 * public class MyEvaluator implements Evaluator {
 *     @Override
 *     public EvaluationResult evaluate(EvaluationContext context) {
 *         // 自定义评测逻辑
 *     }
 *     @Override
 *     public Set<EvaluationDimension> supportedDimensions() {
 *         return Set.of(EvaluationDimension.INTELLIGENCE);
 *     }
 *     @Override
 *     public EvaluationLevel level() { return EvaluationLevel.LLM_JUDGE; }
 * }
 * }</pre>
 */
public interface Evaluator {

    /**
     * 执行评测。
     *
     * @param context 评测上下文
     * @return 评测结果（包含该评测器支持的维度评分）
     */
    EvaluationResult evaluate(EvaluationContext context);

    /**
     * 该评测器支持的维度集合。
     *
     * @return 支持的评测维度
     */
    Set<EvaluationDimension> supportedDimensions();

    /**
     * 该评测器的层级。
     *
     * @return 评测层级（RULE / LLM_JUDGE / HUMAN）
     */
    EvaluationLevel level();
}
