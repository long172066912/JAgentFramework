package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AgentScope Agent 适配器 — 将 AgentScope {@link HarnessAgent}
 * 包装为 jagent-core 的 {@link Agent} 接口。
 *
 * <p>同步执行经过拦截器链包装，实现 AOP 统一抽象。
 */
public class AgentScopeAgentAdapter implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeAgentAdapter.class);

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
     * @param delegate          AgentScope HarnessAgent 实例
     * @param interceptors      同步拦截器列表
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate,
                                   List<AgentInterceptor> interceptors) {
        this.delegate = delegate;
        this.interceptors = interceptors != null ? new ArrayList<>(interceptors) : List.of();
        log.info("AgentScopeAgentAdapter created: agentId={} interceptors={}",
                delegate.getAgentId(), this.interceptors.size());
    }

    @Override
    public String id() {
        return delegate.getAgentId();
    }

    @Override
    public String name() {
        return delegate.getName();
    }

    // ==================== 同步执行 ====================

    /**
     * 同步执行 Agent — 经过拦截器链包装。
     */
    @Override
    public TaskResult execute(ChatMessage input, AgentContext context) {
        AgentInterceptor.ExecutionChain chain = buildSyncChain();

        // 前置通知
        for (AgentInterceptor interceptor : interceptors) {
            interceptor.beforeExecute(this, input, context);
        }

        try {
            // 环绕执行
            TaskResult result = executeWithAround(input, context, chain, 0);

            // 后置通知
            for (AgentInterceptor interceptor : interceptors) {
                interceptor.afterExecute(this, input, context, result);
            }

            // 自动合并拦截器产生的额外步骤
            result = enrichTraceWithInterceptorSteps(result, context);

            return result;
        } catch (Exception e) {
            for (AgentInterceptor interceptor : interceptors) {
                interceptor.onError(this, input, context, e);
            }
            throw e;
        }
    }

    private TaskResult executeWithAround(ChatMessage input, AgentContext context,
                                          AgentInterceptor.ExecutionChain chain, int index) {
        if (index >= interceptors.size()) {
            return chain.proceed(input, context);
        }
        return interceptors.get(index).aroundExecute(this, input, context,
                (i, c) -> executeWithAround(i, c, chain, index + 1));
    }

    private AgentInterceptor.ExecutionChain buildSyncChain() {
        return (input, context) -> {
            Msg asMsg = MessageConverter.toAgentScope(input);
            RuntimeContext asCtx = ContextConverter.toAgentScope(context);
            
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

    // ==================== 流式执行 ====================

    /**
     * 流式执行 Agent — 通过回调通知文本增量事件。
     *
     * <p>利用 Agent 原生的 streamEvents() 能力，通过虚拟线程异步调度，
     * 每收到一个文本增量就通过回调通知调用方。
     * 框架层不依赖 Flux，由调用方自行决定如何消费回调。
     *
     * @param input    用户输入消息
     * @param context  运行时上下文
     * @param onDelta  文本增量回调（每个 chunk 调用一次）
     * @param onComplete 流完成回调
     * @param onError  异常回调
     */
    public void streamEvents(ChatMessage input, AgentContext context,
                              java.util.function.Consumer<String> onDelta,
                              Runnable onComplete,
                              java.util.function.Consumer<Throwable> onError) {
        Msg asMsg = MessageConverter.toAgentScope(input);
        RuntimeContext asCtx = ContextConverter.toAgentScope(context);

        log.info("[Stream] Starting streamEvents for agent={}", id());

        // 通过虚拟线程异步启动流式执行，通过回调通知事件
        Thread.startVirtualThread(() ->
            delegate.streamEvents(asMsg, asCtx)
                    .filter(event -> event.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                    .map(event -> {
                        if (event instanceof TextBlockDeltaEvent delta) {
                            return delta.getDelta();
                        }
                        return "";
                    })
                    .filter(s -> !s.isEmpty())
                    .subscribe(
                            onDelta,
                            onError::accept,
                            onComplete::run
                    )
        );
    }

    /**
     * 获取底层 AgentScope HarnessAgent 实例。
     */
    public HarnessAgent getDelegate() {
        return delegate;
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private TaskResult enrichTraceWithInterceptorSteps(TaskResult result, AgentContext context) {
        List<ExecutionTrace.Step> evalSteps = context.<List<ExecutionTrace.Step>>get("jagent.evaluation.steps").orElse(null);
        Long evalTime = context.<Long>get("jagent.evaluation.time").orElse(0L);

        if (evalSteps == null || evalSteps.isEmpty()) {
            return result;
        }

        ExecutionTrace originalTrace = result.trace();
        List<ExecutionTrace.Step> allSteps = new ArrayList<>();
        if (originalTrace != null) {
            allSteps.addAll(originalTrace.steps());
        }
        allSteps.addAll(evalSteps);

        long totalTime = (originalTrace != null ? originalTrace.totalTime() : 0) + evalTime;
        ExecutionTrace enrichedTrace = new ExecutionTrace(List.copyOf(allSteps), totalTime);

        return result.withTrace(enrichedTrace);
    }
}
