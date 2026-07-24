package com.jrl.ai.agent.core.memory;

import com.jrl.ai.agent.core.io.ChatMessage;

import java.util.List;

/**
 * 对话记忆 — 管理会话历史消息
 */
public interface ConversationMemory {

    /**
     * 添加消息
     */
    void addMessage(String sessionId, ChatMessage message);

    /**
     * 获取历史消息
     */
    List<ChatMessage> getMessages(String sessionId);

    /**
     * 获取最近 N 条消息
     */
    List<ChatMessage> getRecentMessages(String sessionId, int limit);

    /**
     * 清空会话记忆
     */
    void clear(String sessionId);
}
