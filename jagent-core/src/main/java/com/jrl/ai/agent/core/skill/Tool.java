package com.jrl.ai.agent.core.skill;

/**
 * 工具 — Agent 可调用的外部能力（Skill 的底层抽象）
 */
public interface Tool {

    /**
     * 工具名称
     */
    String name();

    /**
     * 工具描述（供 LLM 理解）
     */
    String description();

    /**
     * 参数 Schema（JSON Schema 格式）
     */
    String parametersSchema();

    /**
     * 执行工具调用
     */
    String execute(String arguments);
}
