package com.jrl.ai.agent.core.prompt;

import java.util.Optional;

/**
 * 提示词注册表 — 按名称/版本管理提示词模板。
 *
 * <p>提供提示词模板的注册、查找和注销能力，
 * 支持同一模板的多版本管理。与 {@link PromptTemplate} 配合使用。
 *
 * @see PromptTemplate
 */
public interface PromptRegistry {

    /**
     * 注册提示词模板。
     *
     * @param template 待注册的模板
     */
    void register(PromptTemplate template);

    /**
     * 按名称查找最新版本的模板。
     *
     * @param name 模板名称
     * @return 匹配的模板，不存在时返回 {@link Optional#empty()}
     */
    Optional<PromptTemplate> get(String name);

    /**
     * 按名称和版本查找模板。
     *
     * @param name    模板名称
     * @param version 版本号
     * @return 匹配的模板，不存在时返回 {@link Optional#empty()}
     */
    Optional<PromptTemplate> get(String name, String version);

    /**
     * 注销指定名称的所有版本模板。
     *
     * @param name 模板名称
     */
    void unregister(String name);
}
