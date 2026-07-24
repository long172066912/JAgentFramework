package com.jrl.ai.agent.core.io;

/**
 * 工具调用结果内容块
 */
public record ToolResultBlock(String callId, String result, boolean isError) implements ContentBlock {

    @Override
    public String type() {
        return "tool_result";
    }
}
