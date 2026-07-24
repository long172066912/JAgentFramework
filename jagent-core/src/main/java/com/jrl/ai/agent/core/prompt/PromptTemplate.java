package com.jrl.ai.agent.core.prompt;

import java.util.Map;

/**
 * 提示词模板 — 支持变量替换
 */
public interface PromptTemplate {

    /**
     * 模板名称
     */
    String name();

    /**
     * 模板内容（含占位符）
     */
    String template();

    /**
     * 渲染模板，替换变量
     */
    String render(Map<String, Object> variables);

    /**
     * 获取模板中定义的变量名
     */
    java.util.Set<String> variableNames();
}
