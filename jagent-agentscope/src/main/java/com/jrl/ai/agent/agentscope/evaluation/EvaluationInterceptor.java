package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.agentscope.tracing.EvaluationSpanProcessor;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.evaluation.trace.SpanData;
import com.jrl.ai.agent.core.evaluation.trace.TraceAnalysis;
import com.jrl.ai.agent.core.evaluation.trace.TraceAnalyzer;
import com.jrl.ai.agent.core.evaluation.trace.TraceBasedEvaluator;
import com.jrl.ai.agent.core.evaluation.trace.TraceSnapshot;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测拦截器 — Agent 执行完成后自动触发评测链。
 *
 * <p>通过配置 {@code jagent.evaluation.enabled=true} 启用，
 * 自动收集所有已注册的 {@link Evaluator} Bean 并依次执行评测。
 *
 * <p>默认异步模式：评测链（含 LLM 评测与优化分析，可能耗时 10s+）在虚拟线程中执行，
 * 不阻塞业务响应；结果由 {@link EvaluationStore} 持久化，后续通过查询接口获取。
 * 需要同步返回评测时，构造时传 {@code async=false}。
 */
public class EvaluationInterceptor implements AgentInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EvaluationInterceptor.class);

    /** 评测 span 名称前缀（回写到执行链路，供追踪后端查看评分） */
    private static final String EVALUATION_SPAN_PREFIX = "agent.evaluation";

    /** OTel 追踪器名称（instrumentation scope） */
    private static final String TRACER_NAME = "com.jrl.ai.agent.evaluation";

    /** 默认等待链路 span 落库的最长时间（ms） */
    private static final long DEFAULT_TRACE_AWAIT_MS = 500L;

    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;
    private final EvaluationStore store;
    private final OptimizationAnalyzer optimizationAnalyzer;
    private final OptimizationReportStore optimizationReportStore;
    private final double confidenceThreshold;
    private final EvaluationSpanProcessor spanProcessor;
    private final long traceAwaitMs;
    /** 是否异步执行评测链（true 时不阻塞业务响应，评测在虚拟线程中执行） */
    private final boolean async;

    /**
     * 创建评测拦截器（不含优化分析）。
     *
     * @param evaluators       所有已注册的评测器
     * @param compositeScorer  复合评分器
     * @param store            评测结果存储
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store) {
        this(evaluators, compositeScorer, store, null, null, 0.8);
    }

    /**
     * 创建评测拦截器（含优化分析）。
     *
     * @param evaluators              所有已注册的评测器
     * @param compositeScorer         复合评分器
     * @param store                   评测结果存储
     * @param optimizationAnalyzer    优化分析器（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store,
                                 OptimizationAnalyzer optimizationAnalyzer,
                                 OptimizationReportStore optimizationReportStore) {
        this(evaluators, compositeScorer, store, optimizationAnalyzer, optimizationReportStore, 0.8);
    }

    /**
     * 创建评测拦截器（含优化分析和置信度阈值）。
     *
     * @param evaluators              所有已注册的评测器
     * @param compositeScorer         复合评分器
     * @param store                   评测结果存储
     * @param optimizationAnalyzer    优化分析器（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     * @param confidenceThreshold     置信度阈值，低于此分数时触发优化建议
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store,
                                 OptimizationAnalyzer optimizationAnalyzer,
                                 OptimizationReportStore optimizationReportStore,
                                 double confidenceThreshold) {
        this(evaluators, compositeScorer, store, optimizationAnalyzer,
                optimizationReportStore, confidenceThreshold, null, DEFAULT_TRACE_AWAIT_MS);
    }

    /**
     * 创建评测拦截器（含优化分析、置信度阈值与链路分析能力）。
     *
     * @param evaluators              所有已注册的评测器
     * @param compositeScorer         复合评分器
     * @param store                   评测结果存储
     * @param optimizationAnalyzer    优化分析器（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     * @param confidenceThreshold     置信度阈值，低于此分数时触发优化建议
     * @param spanProcessor           链路 span 捕获器（可选，提供后启用基于 trace 的多维分析；
     *                                span 仅请求级暂存，评测后取走即弃，框架不做持久化）
     * @param traceAwaitMs            等待链路 span 就绪的最长时间（ms）
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store,
                                 OptimizationAnalyzer optimizationAnalyzer,
                                 OptimizationReportStore optimizationReportStore,
                                 double confidenceThreshold,
                                 EvaluationSpanProcessor spanProcessor,
                                 long traceAwaitMs) {
        this(evaluators, compositeScorer, store, optimizationAnalyzer,
                optimizationReportStore, confidenceThreshold, spanProcessor, traceAwaitMs, true);
    }

    /**
     * 创建评测拦截器（可指定同步/异步模式）。
     *
     * @param evaluators              所有已注册的评测器
     * @param compositeScorer         复合评分器
     * @param store                   评测结果存储（负责持久化，异步模式下是结果的唯一出口）
     * @param optimizationAnalyzer    优化分析器（可选，随评测链一同异步执行）
     * @param optimizationReportStore 优化报告存储（可选）
     * @param confidenceThreshold     置信度阈值，低于此分数时触发优化建议
     * @param spanProcessor           链路 span 捕获器（可选，提供后启用基于 trace 的多维分析；
     *                                span 仅请求级暂存，评测后取走即弃，框架不做持久化）
     * @param traceAwaitMs            等待链路 span 就绪的最长时间（ms）
     * @param async                   是否异步执行评测链（true 时不阻塞业务响应）
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store,
                                 OptimizationAnalyzer optimizationAnalyzer,
                                 OptimizationReportStore optimizationReportStore,
                                 double confidenceThreshold,
                                 EvaluationSpanProcessor spanProcessor,
                                 long traceAwaitMs,
                                 boolean async) {
        this.evaluators = evaluators;
        this.compositeScorer = compositeScorer;
        this.store = store;
        this.optimizationAnalyzer = optimizationAnalyzer;
        this.optimizationReportStore = optimizationReportStore;
        this.confidenceThreshold = confidenceThreshold;
        this.spanProcessor = spanProcessor;
        this.traceAwaitMs = traceAwaitMs;
        this.async = async;
    }

    /**
     * 是否异步执行评测链。
     *
     * @return true 表示评测不阻塞业务响应
     */
    public boolean isAsync() {
        return async;
    }

    @Override
    public void afterExecute(Agent agent, ChatMessage input, AgentContext context, TaskResult result) {
        if (async) {
            // 异步模式：评测链（含 LLM 评测与优化分析）丢到虚拟线程执行，业务响应立即返回；
            // 结果由 EvaluationStore 持久化，后续通过查询接口获取
            log.info("[Evaluation] async dispatched for agent={}", agent.id());
            Thread.startVirtualThread(() -> doEvaluate(agent, input, context, result));
            return;
        }
        doEvaluate(agent, input, context, result);
    }

    /**
     * 执行完整评测链 — 同步模式下由 afterExecute 直接调用，异步模式下在虚拟线程中执行。
     */
    private void doEvaluate(Agent agent, ChatMessage input, AgentContext context, TaskResult result) {
        log.info("[Evaluation] doEvaluate started for agent={}", agent.id());
        long evalStart = System.currentTimeMillis();

        try {
            // 构建评测上下文
            Map<String, Object> taskResultMap = Map.of(
                    "status", result.status() != null ? result.status().name() : "UNKNOWN",
                    "durationMs", result.durationMs()
            );

            // 链路关联：适配器在同步执行时捕获的 traceId/spanId
            String traceId = context.<String>get(AgentScopeAgentAdapter.CONTEXT_KEY_TRACE_ID).orElse(null);
            String spanId = context.<String>get(AgentScopeAgentAdapter.CONTEXT_KEY_SPAN_ID).orElse(null);

            // 链路分析：请求级暂存 span → 多维分析 → 冻结快照（span 用完即排空），
            // 快照归入执行链路（ExecutionTrace.otel），不放评测结果
            Map<String, Object> metadata = new HashMap<>();
            TraceSnapshot traceSnapshot = collectTraceSnapshot(traceId);
            if (traceSnapshot != null && traceSnapshot.analysis() != null) {
                metadata.put(TraceBasedEvaluator.METADATA_KEY_ANALYSIS, traceSnapshot.analysis());
            }

            EvaluationContext evalContext = new EvaluationContext(
                    agent.id(),
                    result.sessionId(),
                    traceId,
                    input != null ? input.content() : null,
                    extractOutput(result),
                    result.trace(),
                    taskResultMap,
                    metadata
            );

            // 遍历所有评测器，合并评分，记录每个评测器的执行时间
            Map<EvaluationDimension, DimensionScore> allScores = new EnumMap<>(EvaluationDimension.class);
            List<ExecutionTrace.Step> evalSteps = new ArrayList<>();

            for (Evaluator evaluator : evaluators) {
                long stepStart = System.currentTimeMillis();
                try {
                    EvaluationResult evalResult = evaluator.evaluate(evalContext);
                    long stepDuration = System.currentTimeMillis() - stepStart;
                    allScores.putAll(evalResult.scores());
                    
                    // 构建详细的 step 信息
                    String stepDetail = buildEvalStepDetail(evaluator, evalResult);
                    evalSteps.add(new ExecutionTrace.Step(
                            "EVAL_" + evaluator.getClass().getSimpleName().toUpperCase(),
                            stepDuration,
                            stepDetail
                    ));
                } catch (Exception e) {
                    long stepDuration = System.currentTimeMillis() - stepStart;
                    log.warn("[Evaluation] Evaluator {} failed: {}",
                            evaluator.getClass().getSimpleName(), e.getMessage());
                    evalSteps.add(new ExecutionTrace.Step(
                            "EVAL_" + evaluator.getClass().getSimpleName().toUpperCase() + "_FAILED",
                            stepDuration,
                            "error=" + e.getMessage()
                    ));
                }
            }

            // 计算加权总分
            long scoreStart = System.currentTimeMillis();
            double compositeScore = compositeScorer.compute(allScores);
            long scoreDuration = System.currentTimeMillis() - scoreStart;
            evalSteps.add(new ExecutionTrace.Step(
                    "COMPOSITE_SCORE",
                    scoreDuration,
                    String.format("score=%.2f,dims=%d", compositeScore, allScores.size())
            ));

            // 构建包含评测步骤的新链路
            ExecutionTrace originalTrace = result.trace();
            List<ExecutionTrace.Step> allSteps = new ArrayList<>();
            if (originalTrace != null) {
                allSteps.addAll(originalTrace.steps());
            }
            allSteps.addAll(evalSteps);
            long totalEvalTime = System.currentTimeMillis() - evalStart;
            ExecutionTrace enrichedTrace = new ExecutionTrace(List.copyOf(allSteps),
                    originalTrace != null ? originalTrace.totalTime() + totalEvalTime : totalEvalTime,
                    traceSnapshot);

            // 将评测步骤与链路快照存储到上下文中，供适配器自动合并到主链路
            context.put("jagent.evaluation.steps", evalSteps);
            context.put("jagent.evaluation.time", totalEvalTime);
            context.put("jagent.trace.snapshot", traceSnapshot);

            // 构建最终评测结果（链路快照随 ExecutionTrace.otel 返回，不在评测结果中重复携带）
            EvaluationResult finalResult = EvaluationResult.builder(agent.id())
                    .sessionId(result.sessionId())
                    .traceId(traceId)
                    .scores(allScores)
                    .compositeScore(compositeScore)
                    .trace(enrichedTrace)
                    .input(input != null ? input.content() : null)
                    .output(extractOutput(result))
                    .build();

            // 持久化
            store.save(finalResult);

            // 将评测结果回写到执行链路（同一 trace 下的 agent.evaluation span）
            recordEvaluationSpan(traceId, spanId, agent, finalResult);

            log.info("[Evaluation] agent={} composite={} dims={} evalTime={}ms",
                    agent.id(), String.format("%.2f", compositeScore), allScores.size(), totalEvalTime);

            // 触发优化分析（如果配置了优化分析器且分数低于阈值）
            if (optimizationAnalyzer != null && optimizationReportStore != null) {
                if (compositeScore < confidenceThreshold) {
                    long optStart = System.currentTimeMillis();
                    try {
                        log.info("[Optimization] agent={} score={} < threshold={}, triggering optimization analysis",
                                agent.id(), String.format("%.2f", compositeScore), confidenceThreshold);
                        OptimizationReport report = optimizationAnalyzer.analyze(finalResult, evalContext);
                        optimizationReportStore.save(report);
                        long optDuration = System.currentTimeMillis() - optStart;
                        log.info("[Optimization] agent={} suggestions={} time={}ms",
                                agent.id(), report.suggestions().size(), optDuration);
                    } catch (Exception ex) {
                        long optDuration = System.currentTimeMillis() - optStart;
                        log.warn("[Optimization] Failed to analyze agent={}: {} (time={}ms)",
                                agent.id(), ex.getMessage(), optDuration);
                    }
                } else {
                    log.debug("[Optimization] agent={} score={} >= threshold={}, skipping optimization analysis",
                            agent.id(), String.format("%.2f", compositeScore), confidenceThreshold);
                }
            }

        } catch (Exception e) {
            log.error("[Evaluation] Failed to evaluate agent={}: {}", agent.id(), e.getMessage(), e);
        }
    }

    /**
     * 收集并冻结执行链路快照 — 等待 span 就绪后做多维分析，
     * 随即取走并清空请求级缓冲（框架不持久化 span，其归宿是 OTel 追踪后端）。
     *
     * @param traceId 执行链路 trace ID（可为 null）
     * @return 链路快照，无链路数据时返回 null
     */
    private TraceSnapshot collectTraceSnapshot(String traceId) {
        if (spanProcessor == null || traceId == null) {
            return null;
        }
        try {
            spanProcessor.awaitSpans(traceId, traceAwaitMs);
            List<SpanData> spans = spanProcessor.takeSpans(traceId);
            if (spans.isEmpty()) {
                log.debug("[Evaluation] traceId={} 无 span 数据，跳过链路分析", traceId);
                return null;
            }
            TraceAnalysis analysis = TraceAnalyzer.analyze(traceId, spans);
            return new TraceSnapshot(traceId, analysis,
                    spans.stream().map(TraceSnapshot.SpanView::from).toList());
        } catch (Exception e) {
            log.warn("[Evaluation] 链路快照收集失败 traceId={}: {}", traceId, e.getMessage());
            spanProcessor.takeSpans(traceId);
            return null;
        }
    }

    /**
     * 将评测结果回写到 OTel 链路 — 在执行根 span 下创建 {@code agent.evaluation} span，
     * 携带总分与各维度评分属性，追踪后端可直接查看评分。
     *
     * <p>未配置 OTel SDK 时为 noop，零开销。
     */
    private void recordEvaluationSpan(String traceId, String parentSpanId,
                                      Agent agent, EvaluationResult finalResult) {
        if (traceId == null || parentSpanId == null) {
            return;
        }
        try {
            SpanContext parentContext = SpanContext.create(
                    traceId, parentSpanId, TraceFlags.getSampled(), TraceState.getDefault());
            Span evalSpan = GlobalOpenTelemetry.getTracer(TRACER_NAME)
                    .spanBuilder(EVALUATION_SPAN_PREFIX)
                    .setParent(io.opentelemetry.context.Context.root().with(Span.wrap(parentContext)))
                    .setAttribute("jagent.evaluation.id", finalResult.evalId())
                    .setAttribute("jagent.evaluation.composite_score", finalResult.compositeScore())
                    .setAttribute("gen_ai.agent.id", agent.id() != null ? agent.id() : "")
                    .startSpan();
            finalResult.scores().forEach((dimension, score) ->
                    evalSpan.setAttribute("jagent.evaluation.score." + dimension.name().toLowerCase(),
                            score.score()));
            evalSpan.end();
        } catch (Exception e) {
            log.warn("[Evaluation] 评测结果回写链路失败: {}", e.getMessage());
        }
    }

    /**
     * 从 TaskResult 中提取可读的输出文本。
     *
     * <p>优先取 "response" 字段（Agent 标准输出），避免 Map.toString() 产生不可读格式。
     */
    private String extractOutput(TaskResult result) {
        if (result == null || result.result() == null) return null;
        Object response = result.result().get("response");
        if (response != null) return String.valueOf(response);
        // fallback: 尝试其他常见字段
        Object output = result.result().get("output");
        if (output != null) return String.valueOf(output);
        return result.result().toString();
    }

    /**
     * 构建评测步骤的详细信息字符串。
     *
     * @param evaluator  评测器
     * @param evalResult 评测结果
     * @return 详细信息字符串
     */
    private String buildEvalStepDetail(Evaluator evaluator, EvaluationResult evalResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("dims=").append(evalResult.scores().size());
        
        // 如果是 LLM 评测器，添加模型信息
        if (evaluator instanceof LLMJudgeEvaluator llmEvaluator) {
            try {
                String modelName = llmEvaluator.getJudgeModel().modelId();
                sb.append(",model=").append(modelName);
            } catch (Exception e) {
                // 忽略获取模型名失败
            }
        }
        
        return sb.toString();
    }
}
