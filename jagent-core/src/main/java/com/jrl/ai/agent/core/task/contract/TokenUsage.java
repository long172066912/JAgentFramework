package com.jrl.ai.agent.core.task.contract;

/**
 * Token 消耗统计 — 用于监控和计费。
 */
public record TokenUsage(
        /** 输入 token 数 */
        int promptTokens,
        /** 输出 token 数 */
        int completionTokens,
        /** 总 token 数（输入 + 输出） */
        int totalTokens,
        /** 使用的模型 ID */
        String modelId
) {

    /**
     * 快速创建 Token 消耗统计（自动计算 totalTokens）。
     *
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @param modelId          模型 ID
     * @return 新建的 TokenUsage 实例
     */
    public static TokenUsage of(int promptTokens, int completionTokens, String modelId) {
        return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens, modelId);
    }
}
