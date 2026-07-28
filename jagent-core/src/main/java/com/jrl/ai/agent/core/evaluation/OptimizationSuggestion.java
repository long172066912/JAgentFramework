package com.jrl.ai.agent.core.evaluation;

/**
 * 优化建议 — 针对 Agent 执行结果的具体改进建议。
 *
 * @param category   建议分类（PROMPT / SKILL / MODEL / AGENT_STEP）
 * @param priority   优先级（HIGH / MEDIUM / LOW）
 * @param title      建议标题（简短概括）
 * @param content    建议内容（详细说明如何改进）
 * @param reason     依据（为什么给出此建议，关联的评测数据）
 */
public record OptimizationSuggestion(
        SuggestionCategory category,
        Priority priority,
        String title,
        String content,
        String reason
) {

    /**
     * 优先级枚举。
     */
    public enum Priority {
        /** 高优先级 — 显著影响效果，建议立即优化 */
        HIGH("高"),
        /** 中优先级 — 有一定提升空间，建议近期优化 */
        MEDIUM("中"),
        /** 低优先级 — 锦上添花，可择机优化 */
        LOW("低");

        private final String label;

        Priority(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 快捷创建高优先级建议。
     */
    public static OptimizationSuggestion high(SuggestionCategory category, String title, String content, String reason) {
        return new OptimizationSuggestion(category, Priority.HIGH, title, content, reason);
    }

    /**
     * 快捷创建中优先级建议。
     */
    public static OptimizationSuggestion medium(SuggestionCategory category, String title, String content, String reason) {
        return new OptimizationSuggestion(category, Priority.MEDIUM, title, content, reason);
    }

    /**
     * 快捷创建低优先级建议。
     */
    public static OptimizationSuggestion low(SuggestionCategory category, String title, String content, String reason) {
        return new OptimizationSuggestion(category, Priority.LOW, title, content, reason);
    }
}
