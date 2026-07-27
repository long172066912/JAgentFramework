package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.model.ModelConfig;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ModelRegistry;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AgentScope 模型适配器 — 将 AgentScope 的 {@link io.agentscope.core.model.Model}
 * 包装为 jagent-core 的 {@link Model} 接口。
 *
 * <p>通过 AgentScope 的 {@link ModelRegistry} 解析模型字符串（如 "dashscope:qwen-plus"），
 * 自动读取对应环境变量中的 API Key。
 */
public class AgentScopeModelAdapter implements Model {

    private final io.agentscope.core.model.Model delegate;
    private final ModelConfig config;

    /**
     * 通过 AgentScope 原生 Model 实例创建适配器。
     *
     * @param delegate AgentScope 模型实例
     * @param provider 提供商标识（如 "dashscope"、"openai"）
     */
    public AgentScopeModelAdapter(io.agentscope.core.model.Model delegate, String provider) {
        this.delegate = delegate;
        this.config = ModelConfig.of(delegate.getModelName(), provider);
    }

    /**
     * 通过模型引用字符串创建适配器（由 AgentScope ModelRegistry 解析）。
     *
     * @param modelRef 模型引用，格式为 "provider:model"（如 "dashscope:qwen-plus"）
     */
    public AgentScopeModelAdapter(String modelRef) {
        this.delegate = ModelRegistry.resolve(modelRef);
        String provider = modelRef.contains(":") ? modelRef.split(":")[0] : "unknown";
        this.config = ModelConfig.of(delegate.getModelName(), provider);
    }

    @Override
    public ModelConfig config() {
        return config;
    }

    /**
     * 调用模型并返回文本响应。
     *
     * <p>内部将 jagent ChatMessage 转换为 AgentScope Msg，
     * 调用 AgentScope Model 的 stream() 方法，收集所有响应块的文本内容。
     *
     * @param messages jagent 消息列表
     * @return 模型生成的文本
     */
    @Override
    public String call(List<ChatMessage> messages) {
        List<Msg> asMsgs = MessageConverter.toAgentScope(messages);
        Flux<ChatResponse> flux = delegate.stream(asMsgs, null, null);
        // 收集所有响应块，拼接文本
        StringBuilder sb = new StringBuilder();
        flux.toStream().forEach(response -> {
            if (response.getContent() != null) {
                response.getContent().forEach(block -> {
                    if (block instanceof io.agentscope.core.message.TextBlock tb) {
                        sb.append(tb.getText());
                    }
                });
            }
        });
        return sb.toString();
    }

    @Override
    public boolean isAvailable() {
        return true; // AgentScope 内部处理模型容错
    }

    /**
     * 获取底层 AgentScope 原生 Model 实例。
     *
     * @return AgentScope Model
     */
    public io.agentscope.core.model.Model getDelegate() {
        return delegate;
    }
}
