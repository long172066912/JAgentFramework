package com.jrl.ai.agent.core.model;

/**
 * 模型配置 — 描述一个 LLM 模型的基本属性。
 *
 * <p>用于 {@link ModelRegistry} 注册和查找模型，
 * 支持多模型混用场景下的配置管理。
 *
 * @see Model
 * @see ModelRegistry
 */
public record ModelConfig(
        /** 模型唯一标识（如 "qwen-max"、"gpt-4.1"） */
        String modelId,
        /** 模型提供商（如 "dashscope"、"openai"、"anthropic"） */
        String provider,
        /** 模型显示名称 */
        String displayName,
        /** 上下文窗口大小（token 数） */
        int contextWindow,
        /** 是否支持流式输出 */
        boolean streaming,
        /** 是否支持工具调用 */
        boolean toolCalling,
        /** 是否支持视觉输入 */
        boolean vision
) {

    /**
     * 快速创建模型配置。
     *
     * @param modelId  模型 ID
     * @param provider 提供商
     * @return 新建的模型配置
     */
    public static ModelConfig of(String modelId, String provider) {
        return new ModelConfig(modelId, provider, modelId, 0, false, false, false);
    }
}
