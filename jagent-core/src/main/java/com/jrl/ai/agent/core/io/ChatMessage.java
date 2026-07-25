package com.jrl.ai.agent.core.io;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天消息 — Agent 通信的基本单元
 */
public record ChatMessage(
        String id,
        MessageRole role,
        String content,
        Instant timestamp,
        String metadata
) {

    public static ChatMessage of(MessageRole role, String content) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                role,
                content,
                Instant.now(),
                null
        );
    }

    public static ChatMessage system(String content) {
        return of(MessageRole.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return of(MessageRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return of(MessageRole.ASSISTANT, content);
    }
}
