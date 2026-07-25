package com.jrl.ai.agent.core.io;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天消息 — Agent 通信的基本单元。
 *
 * <p>封装一条带有角色标识的文本消息，是 Agent 输入/输出的统一格式。
 * 多模态内容（图片、工具调用等）由适配层在 AgentScope 侧处理，
 * 本层仅保留纯文本抽象以保持轻量。
 *
 * @see MessageRole
 */
public record ChatMessage(
        /** 消息唯一标识 */
        String id,
        /** 消息角色（系统/用户/助手/工具） */
        MessageRole role,
        /** 消息文本内容 */
        String content,
        /** 消息创建时间 */
        Instant timestamp,
        /** 扩展元数据（JSON 字符串），可为空 */
        String metadata
) {

    /**
     * 创建指定角色的消息。
     *
     * @param role    消息角色
     * @param content 文本内容
     * @return 新建的 ChatMessage
     */
    public static ChatMessage of(MessageRole role, String content) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                role,
                content,
                Instant.now(),
                null
        );
    }

    /** 创建系统消息。 */
    public static ChatMessage system(String content) {
        return of(MessageRole.SYSTEM, content);
    }

    /** 创建用户消息。 */
    public static ChatMessage user(String content) {
        return of(MessageRole.USER, content);
    }

    /** 创建助手消息。 */
    public static ChatMessage assistant(String content) {
        return of(MessageRole.ASSISTANT, content);
    }
}
