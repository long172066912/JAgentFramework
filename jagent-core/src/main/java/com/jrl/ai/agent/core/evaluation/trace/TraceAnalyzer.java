package com.jrl.ai.agent.core.evaluation.trace;

import java.util.Collection;

/**
 * Trace 分析器 — 将 span 集合聚合为多维度统计指标。
 *
 * <p>按 {@link SpanData.SpanKind} 分类统计模型调用、工具执行的
 * 次数、耗时、错误与 Token 消耗，供评测体系做链路视角的多维分析。
 */
public final class TraceAnalyzer {

    /** gen_ai 语义约定：输入 Token 属性键 */
    private static final String ATTR_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    /** gen_ai 语义约定：输出 Token 属性键 */
    private static final String ATTR_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    private TraceAnalyzer() {
        // 工具类禁止实例化
    }

    /**
     * 分析 span 集合，生成多维度统计结果。
     *
     * @param traceId 所属 trace ID（可为 null）
     * @param spans   同一 trace 的 span 集合（null 或空返回 {@link TraceAnalysis#EMPTY}）
     * @return 多维分析结果
     */
    public static TraceAnalysis analyze(String traceId, Collection<SpanData> spans) {
        if (spans == null || spans.isEmpty()) {
            return TraceAnalysis.EMPTY;
        }

        int modelCallCount = 0;
        long modelTimeMs = 0;
        int modelErrorCount = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        int toolCallCount = 0;
        long toolTimeMs = 0;
        int toolErrorCount = 0;
        int errorSpanCount = 0;
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;
        long rootDuration = -1;

        for (SpanData span : spans) {
            if (!span.ok()) {
                errorSpanCount++;
            }
            minStart = Math.min(minStart, span.startMs());
            maxEnd = Math.max(maxEnd, span.startMs() + span.durationMs());
            if (span.isRoot()) {
                rootDuration = Math.max(rootDuration, span.durationMs());
            }

            switch (span.kind()) {
                case CHAT -> {
                    modelCallCount++;
                    modelTimeMs += span.durationMs();
                    if (!span.ok()) {
                        modelErrorCount++;
                    }
                    inputTokens += toLong(span.attribute(ATTR_INPUT_TOKENS));
                    outputTokens += toLong(span.attribute(ATTR_OUTPUT_TOKENS));
                }
                case EXECUTE_TOOL -> {
                    toolCallCount++;
                    toolTimeMs += span.durationMs();
                    if (!span.ok()) {
                        toolErrorCount++;
                    }
                }
                default -> {
                    // INVOKE_AGENT / OTHER 只参与总耗时与错误统计
                }
            }
        }

        long totalDurationMs = rootDuration > 0 ? rootDuration : Math.max(0, maxEnd - minStart);
        return new TraceAnalysis(traceId, spans.size(),
                modelCallCount, modelTimeMs, modelErrorCount,
                inputTokens, outputTokens,
                toolCallCount, toolTimeMs, toolErrorCount,
                errorSpanCount, totalDurationMs);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
}
