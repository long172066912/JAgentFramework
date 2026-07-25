package com.jrl.ai.agent.core.skill;

/**
 * 工具 — Agent 可调用的外部能力（Skill 的底层抽象）。
 *
 * <p>Tool 定义了 LLM 工具调用的标准契约，包括名称、描述、
 * 参数 Schema 和执行逻辑。与 {@link Skill} 的区别：
 * Tool 更贴近 LLM 的工具调用协议，Skill 是更高层的业务能力封装。
 */
public interface Tool {

    /**
     * 获取工具名称。
     *
     * @return 工具唯一名称
     */
    String name();

    /**
     * 获取工具描述（供 LLM 理解工具用途）。
     *
     * @return 工具描述文本
     */
    String description();

    /**
     * 获取参数 Schema（JSON Schema 格式）。
     *
     * <p>描述工具接受的参数结构，供 LLM 生成合规的调用参数。
     *
     * @return JSON Schema 字符串
     */
    String parametersSchema();

    /**
     * 执行工具调用。
     *
     * @param arguments LLM 生成的 JSON 格式参数字符串
     * @return 工具执行结果文本
     */
    String execute(String arguments);
}
