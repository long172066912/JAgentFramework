package com.jrl.ai.agent.core.io;

/**
 * 图片内容块
 */
public record ImageBlock(String mimeType, byte[] data) implements ContentBlock {

    @Override
    public String type() {
        return "image";
    }
}
