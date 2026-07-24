package com.jrl.ai.agent.core.prompt;

import com.jrl.ai.agent.core.io.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词构建器 — 流式组装消息列表
 */
public class PromptBuilder {

    private final List<ChatMessage> messages = new ArrayList<>();

    public PromptBuilder system(String text) {
        messages.add(ChatMessage.system(text));
        return this;
    }

    public PromptBuilder user(String text) {
        messages.add(ChatMessage.user(text));
        return this;
    }

    public PromptBuilder assistant(String text) {
        messages.add(ChatMessage.assistant(text));
        return this;
    }

    public PromptBuilder message(ChatMessage message) {
        messages.add(message);
        return this;
    }

    public List<ChatMessage> build() {
        return List.copyOf(messages);
    }
}
