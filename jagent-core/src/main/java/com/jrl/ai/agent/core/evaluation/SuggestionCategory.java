package com.jrl.ai.agent.core.evaluation;

/**
 * 优化建议分类 — 标识建议所属的优化维度。
 *
 * <p>四大优化方向：
 * <ul>
 *   <li>{@link #PROMPT} — 提示词优化</li>
 *   <li>{@link #SKILL} — Skill 能力优化</li>
 *   <li>{@link #MODEL} — 模型选型推荐</li>
 *   <li>{@link #AGENT_STEP} — Agent 编排与步骤优化</li>
 * </ul>
 */
public enum SuggestionCategory {

    /** 提示词优化 — 改进系统提示词、用户提示词模板 */
    PROMPT("提示词优化"),

    /** Skill 优化 — 新增/调整/替换 Skill 能力 */
    SKILL("Skill 优化"),

    /** 模型推荐 — 推荐更合适的大模型 */
    MODEL("模型推荐"),

    /** Agent 与步骤优化 — 改进编排流程、减少冗余步骤 */
    AGENT_STEP("Agent 与步骤优化");

    private final String description;

    SuggestionCategory(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
