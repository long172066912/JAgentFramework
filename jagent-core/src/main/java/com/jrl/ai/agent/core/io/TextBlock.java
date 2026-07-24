package com.jrl.ai.agent.core.io;

/**
 * 文本内容块
 */
public record TextBlock(String text) implements ContentBlock {

    @Override
    public String type() {
        return "text";
    }
}
