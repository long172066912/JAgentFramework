package com.jrl.ai.agent.core.evaluation.trace;

import java.util.Map;

/**
 * Span 数据快照 — 执行链路中单个 span 的结构化表示。
 *
 * <p>由桥接层（如 OpenTelemetry SpanProcessor）从真实 span 转换而来，
 * core 层据此进行与具体追踪实现无关的多维度分析。
 *
 * @param traceId     所属 trace ID
 * @param spanId      span ID
 * @param parentSpanId 父 span ID（根 span 为 null）
 * @param name        span 名称（如 {@code invoke_agent chat}、{@code chat qwen-max}）
 * @param kind        span 类型（Agent 调用 / 模型调用 / 工具执行 / 其他）
 * @param startMs     开始时间戳（epoch 毫秒）
 * @param durationMs  耗时（毫秒）
 * @param ok          是否成功（span 状态非 ERROR）
 * @param attributes  span 属性快照（如 token 用量、工具名）
 */
public record SpanData(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        SpanKind kind,
        long startMs,
        long durationMs,
        boolean ok,
        Map<String, Object> attributes
) {

    /**
     * Span 类型 — 按 Agent 执行语义分类。
     */
    public enum SpanKind {
        /** Agent 整体调用（对应 invoke_agent span） */
        INVOKE_AGENT,
        /** 模型 API 调用（对应 chat span，含 token 用量） */
        CHAT,
        /** 工具执行（对应 execute_tool span） */
        EXECUTE_TOOL,
        /** 其他（框架根 span 或未识别的 span） */
        OTHER
    }

    /**
     * 判断是否为根 span（无父 span）。
     *
     * @return true 表示根 span
     */
    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isEmpty();
    }

    /**
     * 获取属性值。
     *
     * @param key 属性键
     * @return 属性值，不存在时返回 null
     */
    public Object attribute(String key) {
        return attributes != null ? attributes.get(key) : null;
    }
}
