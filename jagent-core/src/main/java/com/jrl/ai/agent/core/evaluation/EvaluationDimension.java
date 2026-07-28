package com.jrl.ai.agent.core.evaluation;

/**
 * 评测维度 — 五维评估模型。
 *
 * <p>Agent 输出质量从五个维度综合评测，缺一不可：
 * <ul>
 *   <li>{@link #INTELLIGENCE} — 智能：输出质量、相关性、完整性</li>
 *   <li>{@link #PERFORMANCE} — 性能：延迟、吞吐量、Token 消耗</li>
 *   <li>{@link #RELIABILITY} — 可靠性：成功率、一致性、稳定性</li>
 *   <li>{@link #SAFETY} — 安全：内容安全、Prompt 泄露、合规性</li>
 *   <li>{@link #EXPERIENCE} — 体验：用户满意度、交互质量</li>
 * </ul>
 */
public enum EvaluationDimension {
    /** 智能：输出质量、相关性、完整性 */
    INTELLIGENCE,
    /** 性能：延迟、吞吐量、Token 消耗 */
    PERFORMANCE,
    /** 可靠性：成功率、一致性、稳定性 */
    RELIABILITY,
    /** 安全：内容安全、Prompt 泄露、合规性 */
    SAFETY,
    /** 体验：用户满意度、交互质量 */
    EXPERIENCE
}
