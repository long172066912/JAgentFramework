package com.jrl.ai.agent.core.prompt;

import com.jrl.ai.agent.core.io.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词构建器 — 流式组装消息列表。
 *
 * <p>提供链式 API 快速构建包含系统、用户、助手消息的对话列表，
 * 最终通过 {@link #build()} 产出不可变的消息集合。
 *
 * <pre>{@code
 * List<ChatMessage> messages = new PromptBuilder()
 *     .system("你是一个专业的翻译助手")
 *     .user("请翻译以下内容")
 *     .build();
 * }</pre>
 */
public class PromptBuilder {

    /** 已添加的消息列表 */
    private final List<ChatMessage> messages = new ArrayList<>();

    /** 添加系统消息。 */
    public PromptBuilder system(String text) {
        messages.add(ChatMessage.system(text));
        return this;
    }

    /** 添加用户消息。 */
    public PromptBuilder user(String text) {
        messages.add(ChatMessage.user(text));
        return this;
    }

    /** 添加助手消息。 */
    public PromptBuilder assistant(String text) {
        messages.add(ChatMessage.assistant(text));
        return this;
    }

    /** 添加任意角色的消息。 */
    public PromptBuilder message(ChatMessage message) {
        messages.add(message);
        return this;
    }

    /**
     * 构建不可变的消息列表。
     *
     * @return 消息列表的不可变副本
     */
    public List<ChatMessage> build() {
        return List.copyOf(messages);
    }
}
