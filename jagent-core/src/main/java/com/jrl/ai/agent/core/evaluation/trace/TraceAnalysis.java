package com.jrl.ai.agent.core.evaluation.trace;

/**
 * Trace 多维分析结果 — 对单次执行的 span 集合做结构化统计。
 *
 * <p>供评测器（如 {@code TraceBasedEvaluator}）从链路视角评估
 * 性能耗时分布、Token 效率、工具使用模式与可靠性。
 *
 * @param traceId         trace ID（可能为 null，表示无链路数据）
 * @param spanCount       span 总数
 * @param modelCallCount  模型调用次数（chat span 数，即推理迭代次数）
 * @param modelTimeMs     模型调用累计耗时
 * @param modelErrorCount 模型调用失败次数
 * @param inputTokens     累计输入 Token
 * @param outputTokens    累计输出 Token
 * @param toolCallCount   工具执行次数（execute_tool span 数）
 * @param toolTimeMs      工具执行累计耗时
 * @param toolErrorCount  工具执行失败次数
 * @param errorSpanCount  错误 span 总数（全部类型）
 * @param totalDurationMs 整体耗时（根 span 或首尾 span 跨度）
 */
public record TraceAnalysis(
        String traceId,
        int spanCount,
        int modelCallCount,
        long modelTimeMs,
        int modelErrorCount,
        long inputTokens,
        long outputTokens,
        int toolCallCount,
        long toolTimeMs,
        int toolErrorCount,
        int errorSpanCount,
        long totalDurationMs
) {

    /** 无链路数据的空分析结果。 */
    public static final TraceAnalysis EMPTY = new TraceAnalysis(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /**
     * 是否存在有效链路数据。
     *
     * @return true 表示有 span 数据
     */
    public boolean hasData() {
        return spanCount > 0;
    }

    /**
     * 模型耗时占比（0.0 ~ 1.0）— 反映瓶颈是否在 LLM 推理。
     *
     * @return 模型耗时 / 总耗时，无数据时返回 0
     */
    public double modelTimeRatio() {
        return totalDurationMs > 0 ? Math.min(1.0, (double) modelTimeMs / totalDurationMs) : 0.0;
    }

    /**
     * 工具耗时占比（0.0 ~ 1.0）— 反映瓶颈是否在工具执行。
     *
     * @return 工具耗时 / 总耗时，无数据时返回 0
     */
    public double toolTimeRatio() {
        return totalDurationMs > 0 ? Math.min(1.0, (double) toolTimeMs / totalDurationMs) : 0.0;
    }

    /**
     * 总 Token 消耗。
     *
     * @return 输入 + 输出 Token
     */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * 工具错误率（0.0 ~ 1.0）。
     *
     * @return 失败工具调用 / 工具调用总数，无工具调用时返回 0
     */
    public double toolErrorRate() {
        return toolCallCount > 0 ? (double) toolErrorCount / toolCallCount : 0.0;
    }

    /**
     * 模型错误率（0.0 ~ 1.0）。
     *
     * @return 失败模型调用 / 模型调用总数，无模型调用时返回 0
     */
    public double modelErrorRate() {
        return modelCallCount > 0 ? (double) modelErrorCount / modelCallCount : 0.0;
    }

    /**
     * 错误 span 占比（全部类型）。
     *
     * @return 错误 span 数 / span 总数，无 span 时返回 0
     */
    public double errorRatio() {
        return spanCount > 0 ? (double) errorSpanCount / spanCount : 0.0;
    }
}
