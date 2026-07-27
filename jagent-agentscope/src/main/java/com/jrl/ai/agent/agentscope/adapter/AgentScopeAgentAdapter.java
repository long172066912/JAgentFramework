package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * AgentScope Agent 适配器 — 将 AgentScope {@link HarnessAgent}
 * 包装为 jagent-core 的 {@link Agent} 接口。
 *
 * <p>执行时自动完成消息格式转换和上下文转换，
 * 底层调用 HarnessAgent 的 {@code call()} 方法（阻塞等待结果）。
 */
public class AgentScopeAgentAdapter implements Agent {

    private final HarnessAgent delegate;

    /**
     * 创建适配器，包装 AgentScope HarnessAgent。
     *
     * @param delegate AgentScope HarnessAgent 实例
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String id() {
        return delegate.getAgentId();
    }

    @Override
    public String name() {
        return delegate.getName();
    }

    /**
     * 同步执行 Agent。
     *
     * <p>将 jagent ChatMessage 转换为 AgentScope Msg，
     * 将 AgentContext 转换为 RuntimeContext，
     * 调用 HarnessAgent.call() 并阻塞等待结果。
     *
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @return 任务执行结果
     */
    @Override
    public TaskResult execute(ChatMessage input, AgentContext context) {
        // 转换消息和上下文
        Msg asMsg = MessageConverter.toAgentScope(input);
        RuntimeContext asCtx = ContextConverter.toAgentScope(context);

        // 调用 AgentScope Agent
        long start = System.currentTimeMillis();
        Mono<Msg> mono = delegate.call(asMsg, asCtx);
        Msg response = mono.block();
        long duration = System.currentTimeMillis() - start;

        if (response == null) {
            return TaskResult.failure(id(), context.sessionId(),
                    "NO_RESPONSE", "Agent 未返回响应", duration);
        }

        // 提取 Token 使用信息
        ChatUsage usage = response.getChatUsage();
        TokenUsage tokenUsage = usage != null
                ? TokenUsage.of(usage.getInputTokens(), usage.getOutputTokens(),
                    delegate.getModel() != null ? delegate.getModel().getModelName() : "unknown")
                : TokenUsage.of(0, 0, "unknown");

        // 构造 jagent TaskResult
        return TaskResult.success(
                id(), context.sessionId(), "text",
                Map.of("response", response.getTextContent(),
                       "model", delegate.getModel() != null ? delegate.getModel().getModelName() : "unknown"),
                tokenUsage,
                duration
        );
    }

    @Override
    public boolean supportsStreaming() {
        return true; // HarnessAgent 原生支持 streamEvents()
    }

    /**
     * 获取底层 AgentScope HarnessAgent 实例。
     *
     * @return HarnessAgent
     */
    public HarnessAgent getDelegate() {
        return delegate;
    }
}
