package com.jrl.ai.agent.core.model;

import com.jrl.ai.agent.core.io.ChatMessage;

import java.util.List;

/**
 * 模型 — LLM 的统一抽象接口。
 *
 * <p>屏蔽不同模型提供商的 API 差异，提供统一的调用契约。
 * 适配层负责将具体模型（AgentScope ChatModel、Spring AI ChatModel 等）
 * 桥接到此接口。
 *
 * <p>支持多模型混用：不同的 Agent 或 Skill 可使用不同的模型，
 * 通过 {@link ModelRegistry} 统一管理。
 *
 * @see ModelConfig
 * @see ModelRegistry
 */
public interface Model {

    /**
     * 获取模型配置信息。
     *
     * @return 模型配置
     */
    ModelConfig config();

    /**
     * 获取模型唯一标识。
     *
     * @return 模型 ID
     */
    default String modelId() {
        return config().modelId();
    }

    /**
     * 同步调用模型。
     *
     * @param messages 对话消息列表
     * @return 模型响应文本
     */
    String call(List<ChatMessage> messages);

    /**
     * 检查模型是否可用。
     *
     * <p>用于多模型场景下的故障转移：当主模型不可用时，
     * 可切换到备用模型。
     *
     * @return 若模型当前可用则返回 {@code true}
     */
    default boolean isAvailable() {
        return true;
    }
}
