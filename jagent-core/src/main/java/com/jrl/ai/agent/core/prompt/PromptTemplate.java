package com.jrl.ai.agent.core.prompt;

import java.util.Map;

/**
 * 提示词模板 — 支持变量替换的提示词抽象。
 *
 * <p>模板内容中可包含占位符，通过 {@link #render(Map)} 方法
 * 传入变量值进行渲染。每个模板声明其支持的变量名集合。
 *
 * @see PromptRegistry
 */
public interface PromptTemplate {

    /**
     * 获取模板名称。
     *
     * @return 模板唯一名称
     */
    String name();

    /**
     * 获取模板原始内容（含占位符）。
     *
     * @return 模板字符串
     */
    String template();

    /**
     * 渲染模板，将占位符替换为变量值。
     *
     * @param variables 变量名到值的映射
     * @return 渲染后的完整提示词
     */
    String render(Map<String, Object> variables);

    /**
     * 获取模板中定义的变量名集合。
     *
     * @return 不可变的变量名集合
     */
    java.util.Set<String> variableNames();
}
