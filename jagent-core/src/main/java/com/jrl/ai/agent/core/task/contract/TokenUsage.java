package com.jrl.ai.agent.core.task.contract;

/**
 * Token 消耗统计 — 用于监控和计费
 */
public record TokenUsage(
        /** 输入 token 数 */
        int promptTokens,
        /** 输出 token 数 */
        int completionTokens,
        /** 总 token 数 */
        int totalTokens,
        /** 使用的模型 ID */
        String modelId
) {

    public static TokenUsage of(int promptTokens, int completionTokens, String modelId) {
        return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens, modelId);
    }
}
