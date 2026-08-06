package com.jrl.ai.agent.core.evaluation.trace;

import java.util.List;

/**
 * 链路快照 — 评测时刻冻结的执行链路摘要，嵌入评测结果随数据一同返回。
 *
 * <p>框架不持久化 span：span 数据跟随 OTel 导出链路流向追踪后端
 * （Jaeger/OTLP 等），本快照仅保留评测分析与页面展示所需的最小信息，
 * 作为评测记录的一部分保存。
 *
 * @param traceId  链路追踪 ID
 * @param analysis 多维统计分析
 * @param spans    精简 span 列表（含层级与耗时，用于绘制链路树）
 */
public record TraceSnapshot(String traceId, TraceAnalysis analysis, List<SpanView> spans) {

    /**
     * 精简 span 视图 — 仅保留链路树渲染所需字段（不含完整属性）。
     *
     * @param spanId       span ID
     * @param parentSpanId 父 span ID（根 span 为 null）
     * @param name         span 名称
     * @param kind         span 类型
     * @param startMs      开始时间（epoch ms）
     * @param durationMs   耗时（ms）
     * @param ok           是否成功
     * @param inputTokens  输入 token（仅模型调用，其他为 0）
     * @param outputTokens 输出 token（仅模型调用，其他为 0）
     */
    public record SpanView(String spanId, String parentSpanId, String name,
                           SpanData.SpanKind kind, long startMs, long durationMs,
                           boolean ok, long inputTokens, long outputTokens) {

        /**
         * 从完整 span 数据提取精简视图。
         *
         * @param span 完整 span 数据
         * @return 精简视图
         */
        public static SpanView from(SpanData span) {
            long inputTokens = 0;
            long outputTokens = 0;
            if (span.kind() == SpanData.SpanKind.CHAT) {
                inputTokens = toLong(span.attribute("gen_ai.usage.input_tokens"));
                outputTokens = toLong(span.attribute("gen_ai.usage.output_tokens"));
            }
            return new SpanView(span.spanId(), span.parentSpanId(), span.name(),
                    span.kind(), span.startMs(), span.durationMs(), span.ok(),
                    inputTokens, outputTokens);
        }

        private static long toLong(Object value) {
            return value instanceof Number number ? number.longValue() : 0L;
        }
    }
}
