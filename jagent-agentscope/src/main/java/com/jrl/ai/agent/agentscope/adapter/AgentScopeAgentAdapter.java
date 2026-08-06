package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentExecutionEvent;
import com.jrl.ai.agent.core.agent.AgentExecutionListener;
import com.jrl.ai.agent.core.agent.AgentExecutionListenerRegistry;
import com.jrl.ai.agent.core.agent.AgentExecutionListeners;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.trace.TraceSnapshot;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import reactor.core.publisher.Flux;
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
 * 同步与流式执行均会通知 {@link AgentExecutionListener}（执行开始/结束）。
 */
public class AgentScopeAgentAdapter implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeAgentAdapter.class);

    /** AgentContext 中存放本次执行 OTel traceId 的键名（供评测体系关联链路） */
    public static final String CONTEXT_KEY_TRACE_ID = "jagent.otel.traceId";

    /** AgentContext 中存放本次执行根 spanId 的键名（供评测 span 挂载父级） */
    public static final String CONTEXT_KEY_SPAN_ID = "jagent.otel.spanId";

    /** OTel 追踪器名称（instrumentation scope） */
    private static final String TRACER_NAME = "com.jrl.ai.agent";

    private final HarnessAgent delegate;
    private final List<AgentInterceptor> interceptors;
    private final AgentExecutionListenerRegistry listenerRegistry;
    /** Agent 配置键名（如 chat），用于执行事件标识来源，可为 null */
    private String agentKey;

    /**
     * 创建适配器，包装 AgentScope HarnessAgent。
     *
     * @param delegate AgentScope HarnessAgent 实例
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate) {
        this(delegate, List.of(), new AgentExecutionListenerRegistry());
    }

    /**
     * 创建适配器，包装 AgentScope HarnessAgent，并指定拦截器链。
     *
     * @param delegate          AgentScope HarnessAgent 实例
     * @param interceptors      同步拦截器列表
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate,
                                   List<AgentInterceptor> interceptors) {
        this(delegate, interceptors, new AgentExecutionListenerRegistry());
    }

    /**
     * 创建适配器，包装 AgentScope HarnessAgent，并指定拦截器链与执行监听器（兼容入口）。
     *
     * <p>监听器会被自动注册到内部注册表（作用域监听器按 key 入桶，其余为全局）。
     *
     * @param delegate          AgentScope HarnessAgent 实例
     * @param interceptors      同步拦截器列表
     * @param listeners         执行监听器列表（感知执行开始/结束）
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate,
                                   List<AgentInterceptor> interceptors,
                                   List<AgentExecutionListener> listeners) {
        this(delegate, interceptors, toRegistry(listeners));
    }

    /**
     * 创建适配器，包装 AgentScope HarnessAgent，并指定拦截器链与执行监听器注册表。
     *
     * @param delegate          AgentScope HarnessAgent 实例
     * @param interceptors      同步拦截器列表
     * @param listenerRegistry  执行监听器注册表（按 agentKey 分发执行开始/结束事件）
     */
    public AgentScopeAgentAdapter(HarnessAgent delegate,
                                   List<AgentInterceptor> interceptors,
                                   AgentExecutionListenerRegistry listenerRegistry) {
        this.delegate = delegate;
        this.interceptors = interceptors != null ? new ArrayList<>(interceptors) : List.of();
        this.listenerRegistry = listenerRegistry != null ? listenerRegistry : new AgentExecutionListenerRegistry();
    }

    /**
     * 将监听器列表转换为注册表（作用域监听器按 key 入桶，其余为全局）。
     */
    private static AgentExecutionListenerRegistry toRegistry(List<AgentExecutionListener> listeners) {
        AgentExecutionListenerRegistry registry = new AgentExecutionListenerRegistry();
        if (listeners != null) {
            listeners.forEach(registry::register);
        }
        return registry;
    }

    /**
     * 设置 Agent 配置键名（由 AgentFactory 创建时注入，用于执行事件标识来源）。
     *
     * @param agentKey Agent 配置键名（如 {@code chat}）
     */
    public void setAgentKey(String agentKey) {
        this.agentKey = agentKey;
    }

    /**
     * 获取 Agent 配置键名（可能为 null）。
     *
     * @return Agent 配置键名
     */
    public String getAgentKey() {
        return agentKey;
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
     * 同步执行 Agent — 经过拦截器链包装，并通知执行监听器。
     */
    @Override
    public TaskResult execute(ChatMessage input, AgentContext context) {
        long startTime = System.currentTimeMillis();
        AgentExecutionListeners.notifyStart(listenerRegistry,
                AgentExecutionEvent.start(this, agentKey, input, context, AgentExecutionEvent.ExecutionMode.SYNC));

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

            AgentExecutionListeners.notifyEnd(listenerRegistry,
                    AgentExecutionEvent.end(this, agentKey, input, context,
                            AgentExecutionEvent.ExecutionMode.SYNC, startTime, result, null));

            return result;
        } catch (Exception e) {
            for (AgentInterceptor interceptor : interceptors) {
                interceptor.onError(this, input, context, e);
            }
            AgentExecutionListeners.notifyEnd(listenerRegistry,
                    AgentExecutionEvent.end(this, agentKey, input, context,
                            AgentExecutionEvent.ExecutionMode.SYNC, startTime, null, e));
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

            // 创建框架根 span：AgentScope 的 invoke_agent/chat/execute_tool span 均挂载其下，
            // 评测体系据此捕获完整链路做多维分析（未配置 OTel SDK 时为 noop，零开销）
            Span executionSpan = beginExecutionSpan();
            try (Scope ignored = executionSpan.makeCurrent()) {
                if (executionSpan.getSpanContext().isValid()) {
                    context.put(CONTEXT_KEY_TRACE_ID, executionSpan.getSpanContext().getTraceId());
                    context.put(CONTEXT_KEY_SPAN_ID, executionSpan.getSpanContext().getSpanId());
                }

                ExecutionTrace.Builder traceBuilder = ExecutionTrace.builder().start();

                long start = System.currentTimeMillis();
                Mono<Msg> mono = delegate.call(asMsg, asCtx);
                Msg response = mono.block();
                long duration = System.currentTimeMillis() - start;

                if (response == null) {
                    traceBuilder.step("AGENT_CALL", duration, "agent=%s, status=NO_RESPONSE".formatted(id()));
                    executionSpan.setStatus(StatusCode.ERROR, "NO_RESPONSE");
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

                executionSpan.setStatus(StatusCode.OK);
                return TaskResult.success(
                        id(), context.sessionId(), "text",
                        Map.of("response", response.getTextContent(),
                               "model", delegate.getModel() != null ? delegate.getModel().getModelName() : "unknown"),
                        tokenUsage, duration
                ).withTrace(traceBuilder.build());
            } catch (RuntimeException e) {
                executionSpan.setStatus(StatusCode.ERROR, e.getMessage());
                executionSpan.recordException(e);
                throw e;
            } finally {
                executionSpan.end();
            }
        };
    }

    /**
     * 创建同步执行的框架根 span（未配置 OTel SDK 时返回 noop span，零开销）。
     */
    private Span beginExecutionSpan() {
        Tracer tracer = GlobalOpenTelemetry.getTracer(TRACER_NAME);
        String spanName = agentKey != null ? "jagent.execute " + agentKey : "jagent.execute " + id();
        return tracer.spanBuilder(spanName)
                .setAttribute("jagent.agent.key", agentKey != null ? agentKey : "")
                .setAttribute("jagent.agent.id", id() != null ? id() : "")
                .setAttribute("gen_ai.operation.name", "execute_agent")
                .startSpan();
    }

    // ==================== 流式执行 ====================

    /**
     * 流式执行 Agent — 通过回调通知文本增量事件。
     *
     * <p>利用 Agent 原生的 stream() 能力，通过虚拟线程异步调度，
     * 每收到一个文本增量就通过回调通知调用方。
     * 使用 stream() 而非 streamEvents() 以支持多轮执行（工具调用→最终回复）。
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

        long startTime = System.currentTimeMillis();
        AgentExecutionListeners.notifyStart(listenerRegistry,
                AgentExecutionEvent.start(this, agentKey, input, context, AgentExecutionEvent.ExecutionMode.STREAM));

        Thread.startVirtualThread(() -> {
            final int[] prevLen = {0};
            delegate.stream(asMsg, asCtx)
                    .filter(event -> event.getMessage() != null
                            && event.getMessage().getTextContent() != null)
                    .map(event -> {
                        String fullText = event.getMessage().getTextContent();
                        if (fullText.length() > prevLen[0]) {
                            String delta = fullText.substring(prevLen[0]);
                            prevLen[0] = fullText.length();
                            // SSE 协议以 \n 分隔事件，需转义避免换行符被当作协议边界丢失
                            return delta.replace("\n", "\\n");
                        }
                        return "";
                    })
                    .filter(delta -> !delta.isEmpty())
                    .subscribe(
                            onDelta,
                            error -> {
                                log.error("[Stream] Error", error);
                                onError.accept(error);
                                AgentExecutionListeners.notifyEnd(listenerRegistry,
                                        AgentExecutionEvent.end(this, agentKey, input, context,
                                                AgentExecutionEvent.ExecutionMode.STREAM, startTime, null, error));
                            },
                            () -> {
                                onComplete.run();
                                AgentExecutionListeners.notifyEnd(listenerRegistry,
                                        AgentExecutionEvent.end(this, agentKey, input, context,
                                                AgentExecutionEvent.ExecutionMode.STREAM, startTime, null, null));
                            }
                    );
        });
    }

    /**
     * 获取底层 AgentScope HarnessAgent 实例。
     */
    public HarnessAgent getDelegate() {
        return delegate;
    }

    // ==================== 原始事件流执行 ====================

    /**
     * 流式执行 Agent 并发射原始事件 — 执行监听器通知在内部封装（供异步任务等场景使用）。
     *
     * <p>与 {@link #streamEvents} 不同，本方法发射原始 {@link AgentEvent}
     * （含工具调用确认事件），由调用方自行消费事件流；
     * 每次调用对应一次执行开始/结束通知。
     *
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @return 原始事件流
     */
    public Flux<AgentEvent> streamAgentEvents(ChatMessage input, AgentContext context) {
        return streamAgentEvents(input, MessageConverter.toAgentScope(input), context);
    }

    /**
     * 使用预构建的 AgentScope 消息流式执行（如携带 ConfirmResult 的确认恢复消息），发射原始事件。
     *
     * <p>执行监听器通知在内部封装，调用方无需感知。
     *
     * @param input   逻辑输入消息（用于执行事件描述）
     * @param asMsg   预构建的 AgentScope 消息
     * @param context 运行时上下文
     * @return 原始事件流
     */
    public Flux<AgentEvent> streamAgentEvents(ChatMessage input, Msg asMsg, AgentContext context) {
        long startTime = System.currentTimeMillis();
        AgentExecutionListeners.notifyStart(listenerRegistry,
                AgentExecutionEvent.start(this, agentKey, input, context, AgentExecutionEvent.ExecutionMode.ASYNC));

        RuntimeContext asCtx = ContextConverter.toAgentScope(context);
        return delegate.streamEvents(asMsg, asCtx)
                .doOnError(error -> AgentExecutionListeners.notifyEnd(listenerRegistry,
                        AgentExecutionEvent.end(this, agentKey, input, context,
                                AgentExecutionEvent.ExecutionMode.ASYNC, startTime, null, error)))
                .doOnComplete(() -> AgentExecutionListeners.notifyEnd(listenerRegistry,
                        AgentExecutionEvent.end(this, agentKey, input, context,
                                AgentExecutionEvent.ExecutionMode.ASYNC, startTime, null, null)));
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private TaskResult enrichTraceWithInterceptorSteps(TaskResult result, AgentContext context) {
        List<ExecutionTrace.Step> evalSteps = context.<List<ExecutionTrace.Step>>get("jagent.evaluation.steps").orElse(null);
        Long evalTime = context.<Long>get("jagent.evaluation.time").orElse(0L);
        TraceSnapshot traceSnapshot = context.<TraceSnapshot>get("jagent.trace.snapshot").orElse(null);

        boolean hasEvalSteps = evalSteps != null && !evalSteps.isEmpty();
        if (!hasEvalSteps && traceSnapshot == null) {
            return result;
        }

        ExecutionTrace originalTrace = result.trace();
        List<ExecutionTrace.Step> allSteps = new ArrayList<>();
        if (originalTrace != null) {
            allSteps.addAll(originalTrace.steps());
        }
        if (hasEvalSteps) {
            allSteps.addAll(evalSteps);
        }

        long totalTime = (originalTrace != null ? originalTrace.totalTime() : 0) + evalTime;
        ExecutionTrace enrichedTrace = new ExecutionTrace(List.copyOf(allSteps), totalTime, traceSnapshot);

        return result.withTrace(enrichedTrace);
    }
}
