package com.jrl.ai.agent.core.mock;

import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.model.ModelConfig;

import java.util.List;
import java.util.function.Function;

/**
 * Mock LLM 模型 — 用于测试的模拟模型实现。
 *
 * <p>支持自定义响应函数，可模拟各种 LLM 行为。
 */
public class MockModel implements Model {

    private final ModelConfig config;
    private Function<List<ChatMessage>, String> responseFunction;
    private int callCount = 0;
    private boolean available = true;

    public MockModel(String modelId, String provider) {
        this.config = ModelConfig.of(modelId, provider);
        // 默认响应：回显最后一条用户消息
        this.responseFunction = messages -> {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).role() == com.jrl.ai.agent.core.io.MessageRole.USER) {
                    return "[Mock:" + modelId + "] 收到: " + messages.get(i).content();
                }
            }
            return "[Mock:" + modelId + "] 无用户输入";
        };
    }

    /**
     * 设置自定义响应函数。
     */
    public MockModel withResponse(Function<List<ChatMessage>, String> fn) {
        this.responseFunction = fn;
        return this;
    }

    /**
     * 设置固定响应。
     */
    public MockModel withFixedResponse(String response) {
        this.responseFunction = messages -> response;
        return this;
    }

    /**
     * 设置模型可用性。
     */
    public MockModel withAvailable(boolean available) {
        this.available = available;
        return this;
    }

    @Override
    public ModelConfig config() {
        return config;
    }

    @Override
    public String call(List<ChatMessage> messages) {
        callCount++;
        return responseFunction.apply(messages);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public int getCallCount() {
        return callCount;
    }
}
