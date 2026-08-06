package com.jrl.ai.agent.core.evaluation.trace;

import com.jrl.ai.agent.core.evaluation.DimensionScore;
import com.jrl.ai.agent.core.evaluation.EvaluationContext;
import com.jrl.ai.agent.core.evaluation.EvaluationDimension;
import com.jrl.ai.agent.core.evaluation.EvaluationLevel;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.Evaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Trace 的评测器 — 从分布式追踪链路视角做多维度分析。
 *
 * <p>依赖评测上下文 metadata 中的 {@link TraceAnalysis}
 * （由评测拦截器通过 SpanProcessor 采集的 span 数据分析得到），覆盖三个维度：
 * <ul>
 *   <li>{@code PERFORMANCE} — 总耗时评分 + 耗时分布诊断（模型/工具耗时占比、Token 消耗）</li>
 *   <li>{@code INTELLIGENCE} — 推理路径效率：模型迭代次数（工具循环检测）、工具调用规模</li>
 *   <li>{@code RELIABILITY} — 链路健康度：错误 span 占比、模型/工具错误率</li>
 * </ul>
 *
 * <p>无链路数据时 PERFORMANCE 回退到执行链路总耗时评分，
 * 其余维度给中性分，不影响其他评测器。
 */
public class TraceBasedEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(TraceBasedEvaluator.class);

    /** 评测上下文 metadata 中 TraceAnalysis 的键名 */
    public static final String METADATA_KEY_ANALYSIS = "jagent.trace.analysis";

    /** 默认延迟阈值（ms） */
    private static final long DEFAULT_LATENCY_THRESHOLD_MS = 10_000L;
    /** 默认理想模型迭代次数（ReAct 轮次）上限 */
    private static final int DEFAULT_IDEAL_ITERATIONS = 3;
    /** 默认最大可接受模型迭代次数，超过即视为工具循环 */
    private static final int DEFAULT_MAX_ITERATIONS = 8;
    /** 默认最大可接受工具调用次数 */
    private static final int DEFAULT_MAX_TOOL_CALLS = 15;

    private final long latencyThresholdMs;
    private final int idealIterations;
    private final int maxIterations;
    private final int maxToolCalls;

    /**
     * 使用默认阈值创建。
     */
    public TraceBasedEvaluator() {
        this(DEFAULT_LATENCY_THRESHOLD_MS, DEFAULT_IDEAL_ITERATIONS,
                DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOOL_CALLS);
    }

    /**
     * 使用自定义阈值创建。
     *
     * @param latencyThresholdMs 延迟阈值（ms），超过则性能分快速衰减
     * @param idealIterations    理想模型迭代次数（ReAct 轮次），不超过则推理效率满分
     * @param maxIterations      最大可接受迭代次数，达到则推理效率 0 分（工具循环）
     * @param maxToolCalls       最大可接受工具调用次数
     */
    public TraceBasedEvaluator(long latencyThresholdMs, int idealIterations,
                               int maxIterations, int maxToolCalls) {
        this.latencyThresholdMs = latencyThresholdMs;
        this.idealIterations = idealIterations;
        this.maxIterations = maxIterations;
        this.maxToolCalls = maxToolCalls;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        TraceAnalysis analysis = extractAnalysis(context);
        Map<EvaluationDimension, DimensionScore> scores = new EnumMap<>(EvaluationDimension.class);

        scores.put(EvaluationDimension.PERFORMANCE, evaluatePerformance(context, analysis));
        scores.put(EvaluationDimension.INTELLIGENCE, evaluateIntelligence(analysis));
        scores.put(EvaluationDimension.RELIABILITY, evaluateReliability(analysis));

        log.debug("[Evaluation] TraceBased agent={} traceId={} perf={} intel={} rel={}",
                context.agentId(), context.traceId(),
                scores.get(EvaluationDimension.PERFORMANCE).score(),
                scores.get(EvaluationDimension.INTELLIGENCE).score(),
                scores.get(EvaluationDimension.RELIABILITY).score());

        return EvaluationResult.builder(context.agentId())
                .sessionId(context.sessionId())
                .traceId(context.traceId())
                .scores(scores)
                .trace(context.trace())
                .input(context.input())
                .output(context.output())
                .build();
    }

    @Override
    public Set<EvaluationDimension> supportedDimensions() {
        return Set.of(EvaluationDimension.PERFORMANCE,
                EvaluationDimension.INTELLIGENCE, EvaluationDimension.RELIABILITY);
    }

    @Override
    public EvaluationLevel level() {
        return EvaluationLevel.RULE;
    }

    // ==================== 维度评测 ====================

    /**
     * PERFORMANCE：总耗时评分 + 耗时分布与 Token 指标。
     */
    private DimensionScore evaluatePerformance(EvaluationContext context, TraceAnalysis analysis) {
        long totalTime;
        if (analysis != null && analysis.hasData() && analysis.totalDurationMs() > 0) {
            totalTime = analysis.totalDurationMs();
        } else if (context.trace() != null && context.trace().totalTime() > 0) {
            // 无链路数据时回退到执行链路耗时
            totalTime = context.trace().totalTime();
            double fallbackScore = latencyScore(totalTime);
            return DimensionScore.of(EvaluationDimension.PERFORMANCE, fallbackScore,
                    EvaluationLevel.RULE, String.format("延迟 %dms / 阈值 %dms（无 trace 数据，回退链路耗时）",
                            totalTime, latencyThresholdMs));
        } else {
            return DimensionScore.of(EvaluationDimension.PERFORMANCE, 0.5,
                    EvaluationLevel.RULE, "无耗时数据");
        }

        double score = latencyScore(totalTime);
        String reason = String.format("延迟 %dms / 阈值 %dms | 模型耗时占比 %.0f%% | 工具耗时占比 %.0f%% | tokens=%d",
                totalTime, latencyThresholdMs,
                analysis.modelTimeRatio() * 100, analysis.toolTimeRatio() * 100, analysis.totalTokens());

        Map<String, Object> metrics = Map.of(
                "totalDurationMs", totalTime,
                "modelTimeMs", analysis.modelTimeMs(),
                "toolTimeMs", analysis.toolTimeMs(),
                "modelTimeRatio", analysis.modelTimeRatio(),
                "toolTimeRatio", analysis.toolTimeRatio(),
                "inputTokens", analysis.inputTokens(),
                "outputTokens", analysis.outputTokens());
        return new DimensionScore(EvaluationDimension.PERFORMANCE, score, EvaluationLevel.RULE, reason, metrics);
    }

    /**
     * INTELLIGENCE：推理路径效率 — 迭代次数与工具调用规模反映规划质量。
     */
    private DimensionScore evaluateIntelligence(TraceAnalysis analysis) {
        if (analysis == null || !analysis.hasData() || analysis.modelCallCount() == 0) {
            return DimensionScore.of(EvaluationDimension.INTELLIGENCE, 0.5,
                    EvaluationLevel.RULE, "无模型调用链路数据");
        }

        int iterations = analysis.modelCallCount();
        double iterationScore;
        if (iterations <= idealIterations) {
            iterationScore = 1.0;
        } else if (iterations >= maxIterations) {
            iterationScore = 0.0;
        } else {
            iterationScore = 1.0 - (double) (iterations - idealIterations) / (maxIterations - idealIterations);
        }

        // 工具调用规模惩罚（过多工具调用说明路径发散）
        double toolScaleScore = analysis.toolCallCount() <= maxToolCalls ? 1.0
                : Math.max(0.0, 1.0 - (double) (analysis.toolCallCount() - maxToolCalls) / maxToolCalls);

        double score = 0.7 * iterationScore + 0.3 * toolScaleScore;
        String reason = iterations >= maxIterations
                ? String.format("检测到工具循环：模型迭代 %d 次 ≥ 上限 %d", iterations, maxIterations)
                : String.format("模型迭代 %d 次，工具调用 %d 次", iterations, analysis.toolCallCount());

        Map<String, Object> metrics = Map.of(
                "modelCallCount", iterations,
                "toolCallCount", analysis.toolCallCount(),
                "totalTokens", analysis.totalTokens());
        return new DimensionScore(EvaluationDimension.INTELLIGENCE, score, EvaluationLevel.RULE, reason, metrics);
    }

    /**
     * RELIABILITY：链路健康度 — 错误 span、模型/工具错误率。
     */
    private DimensionScore evaluateReliability(TraceAnalysis analysis) {
        if (analysis == null || !analysis.hasData()) {
            return DimensionScore.of(EvaluationDimension.RELIABILITY, 0.5,
                    EvaluationLevel.RULE, "无链路数据");
        }

        double errorRatio = analysis.errorRatio();
        double score = Math.max(0.0, 1.0 - errorRatio * 2);
        String reason = errorRatio == 0.0
                ? String.format("链路健康：无错误 span（共 %d 个）", analysis.spanCount())
                : String.format("错误 span 占比 %.0f%%（模型错误率 %.0f%%，工具错误率 %.0f%%）",
                        errorRatio * 100, analysis.modelErrorRate() * 100, analysis.toolErrorRate() * 100);

        Map<String, Object> metrics = Map.of(
                "spanCount", analysis.spanCount(),
                "errorSpanCount", analysis.errorSpanCount(),
                "modelErrorCount", analysis.modelErrorCount(),
                "toolErrorCount", analysis.toolErrorCount());
        return new DimensionScore(EvaluationDimension.RELIABILITY, score, EvaluationLevel.RULE, reason, metrics);
    }

    // ==================== 辅助方法 ====================

    private TraceAnalysis extractAnalysis(EvaluationContext context) {
        if (context.metadata() == null) {
            return null;
        }
        Object analysis = context.metadata().get(METADATA_KEY_ANALYSIS);
        return analysis instanceof TraceAnalysis ta ? ta : null;
    }

    /**
     * 延迟评分：阈值一半内满分，阈值内线性衰减到 0.5，超阈值继续衰减至 0。
     */
    private double latencyScore(long totalTime) {
        if (totalTime <= latencyThresholdMs / 2) {
            return 1.0;
        }
        if (totalTime <= latencyThresholdMs) {
            double ratio = (double) (totalTime - latencyThresholdMs / 2) / (latencyThresholdMs / 2);
            return 1.0 - ratio * 0.5;
        }
        return Math.max(0.0, 0.5 - (double) (totalTime - latencyThresholdMs) / latencyThresholdMs);
    }
}
