package com.jrl.ai.agent.core.prompt;

import java.util.Optional;

/**
 * 提示词注册表 — 按名称/版本管理提示词模板
 */
public interface PromptRegistry {

    /**
     * 注册提示词模板
     */
    void register(PromptTemplate template);

    /**
     * 按名称查找模板
     */
    Optional<PromptTemplate> get(String name);

    /**
     * 按名称和版本查找模板
     */
    Optional<PromptTemplate> get(String name, String version);

    /**
     * 注销模板
     */
    void unregister(String name);
}
