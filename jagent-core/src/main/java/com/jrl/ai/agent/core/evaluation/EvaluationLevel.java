package com.jrl.ai.agent.core.evaluation;

/**
 * 评测层级 — 三层分级评测模型。
 *
 * <p>不同层级的评测器成本和适用场景不同：
 * <ul>
 *   <li>{@link #RULE} — 零成本规则评测，始终执行（如延迟阈值、格式校验）</li>
 *   <li>{@link #LLM_JUDGE} — LLM 评测，按需开启（如语义质量、内容安全）</li>
 *   <li>{@link #HUMAN} — 人工评测，聚焦高风险和主观维度</li>
 * </ul>
 */
public enum EvaluationLevel {
    /** 零成本规则评测，始终执行 */
    RULE,
    /** LLM 评测，按需开启 */
    LLM_JUDGE,
    /** 人工评测，聚焦高风险和主观维度 */
    HUMAN
}
