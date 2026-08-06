package com.jrl.ai.agent.agentscope.tracing;

import com.jrl.ai.agent.core.evaluation.trace.SpanData;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 评测链路 Span 捕获器 — 请求级临时缓冲，不做任何持久化。
 *
 * <p>span 数据的归宿是 OTel 追踪后端（Jaeger/OTLP 等，由导出器负责）；
 * 本捕获器仅在请求窗口内暂存 span，供评测时做多维分析并冻结快照，
 * 评测完成后通过 {@link #takeSpans(String)} 取走并立即清空。
 *
 * <p>注册到 {@code SdkTracerProvider} 后，AgentScope 的 OtelTracingMiddleware
 * 产生的 {@code invoke_agent} / {@code chat} / {@code execute_tool} span
 * 会被捕获用于基于 trace 的多维度评测分析。
 */
public class EvaluationSpanProcessor implements SpanProcessor {

    private static final Logger log = LoggerFactory.getLogger(EvaluationSpanProcessor.class);

    /** gen_ai 语义约定：操作类型属性键 */
    private static final String ATTR_OPERATION_NAME = "gen_ai.operation.name";

    /** 等待根 span 到达的轮询间隔（ms） */
    private static final long POLL_INTERVAL_MS = 20;

    /** 缓冲容量上限（兜底防泄漏，正常情况评测后即清空） */
    private static final int MAX_BUFFERED_TRACES = 500;

    /** 超过此时长未被取走的缓冲直接丢弃（ms） */
    private static final long BUFFER_TTL_MS = 60_000;

    private final Map<String, TraceBuffer> buffers = new ConcurrentHashMap<>();

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // 只关心已结束的 span（含完整属性与耗时）
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        try {
            var spanContext = span.getSpanContext();
            String parentSpanId = span.getParentSpanContext().isValid()
                    ? span.getParentSpanContext().getSpanId() : null;
            long startMs = TimeUnit.NANOSECONDS.toMillis(span.getLatencyNanos() > 0
                    ? System.nanoTime() - span.getLatencyNanos()
                    : System.currentTimeMillis() * 1_000_000L);

            SpanData data = new SpanData(
                    spanContext.getTraceId(),
                    spanContext.getSpanId(),
                    parentSpanId,
                    span.getName(),
                    resolveKind(span),
                    startMs,
                    TimeUnit.NANOSECONDS.toMillis(span.getLatencyNanos()),
                    span.toSpanData().getStatus().getStatusCode() != StatusCode.ERROR,
                    extractAttributes(span)
            );

            TraceBuffer buffer = buffers.computeIfAbsent(data.traceId(), id -> new TraceBuffer());
            synchronized (buffer.spans) {
                buffer.spans.add(data);
            }
            evictStaleIfNeeded();
        } catch (Exception e) {
            log.warn("[EvaluationSpanProcessor] 捕获 span 失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    /**
     * 等待指定 trace 的 span 就绪（根 span 最后结束，其到达意味着链路基本完整）。
     *
     * @param traceId 链路追踪 ID
     * @param waitMs  最长等待时间（ms）
     * @return 当前已捕获的 span 列表（不移除缓冲）
     */
    public List<SpanData> awaitSpans(String traceId, long waitMs) {
        if (traceId == null) {
            return List.of();
        }
        long deadline = System.currentTimeMillis() + Math.max(waitMs, 0);
        while (true) {
            List<SpanData> spans = peek(traceId);
            boolean rootArrived = spans.stream().anyMatch(SpanData::isRoot);
            if (rootArrived || System.currentTimeMillis() >= deadline) {
                return spans;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return spans;
            }
        }
    }

    /**
     * 取走指定 trace 的全部 span 并清空缓冲 — 评测完成后调用，请求窗口结束。
     *
     * @param traceId 链路追踪 ID
     * @return span 列表（无数据时返回空列表）
     */
    public List<SpanData> takeSpans(String traceId) {
        if (traceId == null) {
            return List.of();
        }
        TraceBuffer buffer = buffers.remove(traceId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer.spans) {
            return List.copyOf(buffer.spans);
        }
    }

    /**
     * 当前缓冲的 trace 数量（监控用）。
     *
     * @return trace 数
     */
    public int bufferedTraceCount() {
        return buffers.size();
    }

    private List<SpanData> peek(String traceId) {
        TraceBuffer buffer = buffers.get(traceId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer.spans) {
            return List.copyOf(buffer.spans);
        }
    }

    /**
     * 兜底清理：缓冲超限时丢弃超时未取走的 trace（正常流程评测后即清空，此处仅防泄漏）。
     */
    private void evictStaleIfNeeded() {
        if (buffers.size() <= MAX_BUFFERED_TRACES) {
            return;
        }
        long now = System.currentTimeMillis();
        buffers.entrySet().removeIf(e -> now - e.getValue().createdMs > BUFFER_TTL_MS);
    }

    /**
     * 按 gen_ai 语义约定识别 span 类型。
     */
    private SpanData.SpanKind resolveKind(ReadableSpan span) {
        Object operation = span.getAttribute(AttributeKey.stringKey(ATTR_OPERATION_NAME));
        if (operation == null) {
            return SpanData.SpanKind.OTHER;
        }
        return switch (String.valueOf(operation)) {
            case "invoke_agent" -> SpanData.SpanKind.INVOKE_AGENT;
            case "chat" -> SpanData.SpanKind.CHAT;
            case "execute_tool" -> SpanData.SpanKind.EXECUTE_TOOL;
            default -> SpanData.SpanKind.OTHER;
        };
    }

    /**
     * 提取 span 属性快照（扁平化值）。
     */
    private Map<String, Object> extractAttributes(ReadableSpan span) {
        Map<String, Object> result = new HashMap<>();
        span.toSpanData().getAttributes()
                .forEach((AttributeKey<?> key, Object value) -> result.put(key.getKey(), value));
        return result;
    }

    /**
     * 单条 trace 的临时缓冲条目。
     */
    private static final class TraceBuffer {
        final List<SpanData> spans = new ArrayList<>();
        final long createdMs = System.currentTimeMillis();
    }
}
