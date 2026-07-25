package com.jrl.ai.agent.core.io;

/**
 * 消息角色 — 标识消息的发送方身份。
 *
 * @see ChatMessage
 */
public enum MessageRole {
    /** 系统消息，用于设定 Agent 行为指令 */
    SYSTEM,
    /** 用户消息，来自终端用户输入 */
    USER,
    /** 助手消息，Agent 生成的回复 */
    ASSISTANT,
    /** 工具消息，工具调用的返回结果 */
    TOOL
}
