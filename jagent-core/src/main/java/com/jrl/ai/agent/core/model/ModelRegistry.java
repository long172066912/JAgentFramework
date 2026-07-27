package com.jrl.ai.agent.core.model;

import java.util.Collection;
import java.util.Optional;

/**
 * 模型注册表 — 管理所有可用 LLM 模型的注册与查找。
 *
 * <p>支持多模型混用场景：通过 {@link #resolve(String)} 按名称解析模型，
 * 通过 {@link #defaultModel()} 获取默认模型。
 * 适配层负责将 AgentScope 的 ChatModel 注册到此注册表。
 *
 * @see Model
 */
public interface ModelRegistry {

    /**
     * 注册一个模型。
     *
     * @param model 待注册的模型实例
     */
    void register(Model model);

    /**
     * 按 ID 解析模型。
     *
     * <p>支持 "provider:model" 格式（如 "dashscope:qwen-max"），
     * 也支持直接 modelId 查找。
     *
     * @param modelRef 模型引用标识
     * @return 匹配的模型，不存在时返回 {@link Optional#empty()}
     */
    Optional<Model> resolve(String modelRef);

    /**
     * 获取默认模型。
     *
     * @return 默认模型，未配置时返回 {@link Optional#empty()}
     */
    Optional<Model> defaultModel();

    /**
     * 获取所有已注册的模型。
     *
     * @return 不可变的模型集合
     */
    Collection<Model> all();
}
