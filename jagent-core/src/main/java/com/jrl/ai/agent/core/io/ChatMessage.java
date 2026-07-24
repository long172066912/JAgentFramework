package com.jrl.ai.agent.core.io;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 聊天消息 — Agent 通信的基本单元
 */
public record ChatMessage(
        String id,
        MessageRole role,
        List<ContentBlock> content,
        Instant timestamp,
        String metadata
) {

    public static ChatMessage of(MessageRole role, String text) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                role,
                List.of(new TextBlock(text)),
                Instant.now(),
                null
        );
    }

    public static ChatMessage system(String text) {
        return of(MessageRole.SYSTEM, text);
    }

    public static ChatMessage user(String text) {
        return of(MessageRole.USER, text);
    }

    public static ChatMessage assistant(String text) {
        return of(MessageRole.ASSISTANT, text);
    }

    /**
     * 获取纯文本内容（合并所有 TextBlock）
     */
    public String textContent() {
        return content.stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::text)
                .reduce("", String::concat);
    }
}
