package com.jrl.ai.agent.core.io;

/**
 * 工具调用内容块
 */
public record ToolCallBlock(String toolName, String callId, String arguments) implements ContentBlock {

    @Override
    public String type() {
        return "tool_call";
    }
}
