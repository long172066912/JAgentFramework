package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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
    private final List<AgentInterceptor> interceptors;

    /**
     * 创建适配器，包装 AgentScope HarnessAgent。
     *
     * @param delegate AgentScope HarnessAgent 实例
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate) {
        this(delegate, List.of());
    }

    /**
     * 创建适配器，包装 AgentScope HarnessAgent，并指定拦截器链。
     *
     * @param delegate     AgentScope HarnessAgent 实例
     * @param interceptors Agent 拦截器列表
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate, List<AgentInterceptor> interceptors) {
        this.delegate = delegate;
        this.interceptors = interceptors != null ? new ArrayList<>(interceptors) : List.of();
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

        // 构建拦截器执行链
        AgentInterceptor.ExecutionChain chain = buildChain(asMsg, asCtx);

        // 前置通知
        for (AgentInterceptor interceptor : interceptors) {
            interceptor.beforeExecute(this, input, context);
        }

        try {
            // 环绕执行（最外层拦截器先执行）
            TaskResult result = executeWithAround(input, context, chain, 0);

            // 后置通知
            for (AgentInterceptor interceptor : interceptors) {
                interceptor.afterExecute(this, input, context, result);
            }

            return result;
        } catch (Exception e) {
            // 异常通知
            for (AgentInterceptor interceptor : interceptors) {
                interceptor.onError(this, input, context, e);
            }
            throw e;
        }
    }

    /**
     * 递归执行环绕拦截器链。
     *
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @param chain   底层执行链
     * @param index   当前拦截器索引
     * @return 任务执行结果
     */
    private TaskResult executeWithAround(ChatMessage input, AgentContext context,
                                         AgentInterceptor.ExecutionChain chain, int index) {
        if (index >= interceptors.size()) {
            return chain.proceed(input, context);
        }
        return interceptors.get(index).aroundExecute(this, input, context,
                (i, c) -> executeWithAround(i, c, chain, index + 1));
    }

    /**
     * 构建底层执行链（实际调用 AgentScope）。
     *
     * @param asMsg AgentScope 消息
     * @param asCtx AgentScope 运行时上下文
     * @return 执行链，调用时执行 AgentScope 实际推理
     */
    private AgentInterceptor.ExecutionChain buildChain(Msg asMsg, RuntimeContext asCtx) {
        return (input, context) -> {
            ExecutionTrace.Builder traceBuilder = ExecutionTrace.builder().start();

            long start = System.currentTimeMillis();
            Mono<Msg> mono = delegate.call(asMsg, asCtx);
            Msg response = mono.block();
            long duration = System.currentTimeMillis() - start;

            if (response == null) {
                traceBuilder.step("AGENT_CALL", duration, "agent=%s, status=NO_RESPONSE".formatted(id()));
                return TaskResult.failure(id(), context.sessionId(),
                        "NO_RESPONSE", "Agent 未返回响应", duration)
                        .withTrace(traceBuilder.build());
            }

            ChatUsage usage = response.getChatUsage();
            TokenUsage tokenUsage = usage != null
                    ? TokenUsage.of(usage.getInputTokens(), usage.getOutputTokens(),
                        delegate.getModel() != null ? delegate.getModel().getModelName() : "unknown")
                    : TokenUsage.of(0, 0, "unknown");

            traceBuilder.step("AGENT_CALL", duration,
                    "agent=%s, model=%s, promptTokens=%d, completionTokens=%d".formatted(
                            id(), tokenUsage.modelId(),
                            tokenUsage.promptTokens(), tokenUsage.completionTokens()));

            return TaskResult.success(
                    id(), context.sessionId(), "text",
                    Map.of("response", response.getTextContent(),
                           "model", delegate.getModel() != null ? delegate.getModel().getModelName() : "unknown"),
                    tokenUsage, duration
            ).withTrace(traceBuilder.build());
        };
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
