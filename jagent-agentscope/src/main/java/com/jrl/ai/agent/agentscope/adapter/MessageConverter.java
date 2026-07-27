package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.io.MessageRole;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.UserMessage;

import java.util.List;

/**
 * 消息转换器 — jagent {@link ChatMessage} 与 AgentScope {@link Msg} 的双向转换。
 *
 * <p>jagent 使用简化的 {@code String content} 模型，
 * AgentScope 使用 {@link io.agentscope.core.message.ContentBlock} 体系。
 * 本转换器在两者之间做无损桥接。
 */
public final class MessageConverter {

    private MessageConverter() {}

    /**
     * 将 jagent ChatMessage 转换为 AgentScope Msg。
     *
     * @param message jagent 消息
     * @return AgentScope 消息对象
     */
    public static Msg toAgentScope(ChatMessage message) {
        if (message == null) return null;
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
            case TOOL -> new UserMessage("[Tool Result] " + message.content());
        };
    }

    /**
     * 批量转换 jagent 消息列表为 AgentScope Msg 列表。
     *
     * @param messages jagent 消息列表
     * @return AgentScope 消息列表
     */
    public static List<Msg> toAgentScope(List<ChatMessage> messages) {
        return messages.stream().map(MessageConverter::toAgentScope).toList();
    }

    /**
     * 将 AgentScope Msg 转换为 jagent ChatMessage。
     *
     * @param msg AgentScope 消息
     * @return jagent 消息对象
     */
    public static ChatMessage toJAgent(Msg msg) {
        if (msg == null) return null;
        String text = msg.getTextContent();
        return switch (msg.getRole()) {
            case SYSTEM -> ChatMessage.system(text);
            case USER -> ChatMessage.user(text);
            case ASSISTANT -> ChatMessage.assistant(text);
            case TOOL -> ChatMessage.of(MessageRole.TOOL, text);
        };
    }
}
