package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.agentscope.config.AgentResponseHelper;
import com.jrl.ai.agent.agentscope.tracing.EvaluationSpanProcessor;
import com.jrl.ai.agent.core.agent.Agent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 评测演示 API — 展示评测系统的使用方式。
 */
@RestController
@RequestMapping("/api/demo/evaluation")
public class EvaluationDemoController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationDemoController.class);

    private final AgentFactory agentFactory;
    private final EvaluationStore evaluationStore;
    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;
    private final EvaluationSpanProcessor spanProcessor;

    public EvaluationDemoController(AgentFactory agentFactory,
                                    EvaluationStore evaluationStore,
                                    List<Evaluator> evaluators,
                                    CompositeScorer compositeScorer,
                                    ObjectProvider<EvaluationSpanProcessor> spanProcessor) {
        this.agentFactory = agentFactory;
        this.evaluationStore = evaluationStore;
        this.evaluators = evaluators;
        this.compositeScorer = compositeScorer;
        this.spanProcessor = spanProcessor.getIfAvailable();
    }

    /**
     * 演示：执行一次 Agent 对话并自动评测。
     *
     * @param agentKey Agent 标识（如 "translator"）
     * @param input    用户输入
     * @return 包含对话结果和评测结果的完整响应
     */
    @PostMapping("/run")
    public Map<String, Object> runWithEvaluation(@RequestParam String agentKey,
                                                  @RequestParam String input) {
        log.info("[Demo] 执行评测演示: agent={} input={}", agentKey, input);

        // 在虚拟线程中执行阻塞调用，避免 reactor-http-nio 线程 block 报错
        try {
            return CompletableFuture.supplyAsync(() -> doRun(agentKey, input),
                    Executors.newVirtualThreadPerTaskExecutor()).get();
        } catch (Exception e) {
            log.error("[Demo] 评测执行失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> doRun(String agentKey, String input) {
        Agent agent = agentFactory.getAgent(agentKey);
        AgentContext context = AgentContext.builder()
                .sessionId("demo-" + System.currentTimeMillis())
                .userId("demo-user")
                .build();

        TaskResult result = agent.execute(ChatMessage.user(input), context);

        // 2. 构建评测上下文（携带 OTel traceId 与链路分析结果，span 用完即排空）
        String outputText = extractOutput(result);
        String traceId = context.<String>get("jagent.otel.traceId").orElse(null);
        Map<String, Object> metadata = new HashMap<>();
        TraceSnapshot traceSnapshot = null;
        if (spanProcessor != null && traceId != null) {
            spanProcessor.awaitSpans(traceId, 500L);
            List<SpanData> spans = spanProcessor.takeSpans(traceId);
            if (!spans.isEmpty()) {
                TraceAnalysis analysis = TraceAnalyzer.analyze(traceId, spans);
                metadata.put(TraceBasedEvaluator.METADATA_KEY_ANALYSIS, analysis);
                traceSnapshot = new TraceSnapshot(traceId, analysis,
                        spans.stream().map(TraceSnapshot.SpanView::from).toList());
            }
        }
        EvaluationContext evalContext = new EvaluationContext(
                agent.id(),
                context.sessionId(),
                traceId,
                input,
                outputText,
                result.trace(),
                Map.of("status", result.status().name()),
                metadata
        );

        // 3. 执行所有评测器
        Map<EvaluationDimension, DimensionScore> allScores = new java.util.EnumMap<>(EvaluationDimension.class);
        for (Evaluator evaluator : evaluators) {
            try {
                EvaluationResult evalResult = evaluator.evaluate(evalContext);
                allScores.putAll(evalResult.scores());
            } catch (Exception e) {
                log.warn("[Demo] 评测器 {} 失败: {}", evaluator.getClass().getSimpleName(), e.getMessage());
            }
        }

        // 4. 计算加权总分
        double compositeScore = compositeScorer.compute(allScores);

        // 5. 构建并保存评测结果（链路快照挂在 trace.otel，不放入评测结果）
        ExecutionTrace finalTrace = new ExecutionTrace(
                result.trace() != null ? result.trace().steps() : List.of(),
                result.trace() != null ? result.trace().totalTime() : 0L,
                traceSnapshot);
        EvaluationResult evaluationResult = EvaluationResult.builder(agent.id())
                .sessionId(context.sessionId())
                .traceId(traceId)
                .scores(allScores)
                .compositeScore(compositeScore)
                .trace(finalTrace)
                .input(input)
                .output(outputText)
                .build();

        evaluationStore.save(evaluationResult);

        // 6. 返回完整结果
        return Map.of(
                "agentId", agent.id(),
                "agentName", agent.name(),
                "input", input,
                "output", outputText != null ? outputText : "",
                "duration", result.durationMs() + "ms",
                "trace", AgentResponseHelper.toTraceMap(finalTrace),
                "evaluation", Map.of(
                        "compositeScore", compositeScore,
                        "dimensions", allScores,
                        "evalId", evaluationResult.evalId()
                )
        );
    }

    /**
     * 查看最近的评测结果。
     *
     * @param agentId Agent 标识
     * @param limit   最大返回数量
     * @return 评测结果列表
     */
    @GetMapping("/results/{agentId}")
    public List<EvaluationResult> getRecentResults(@PathVariable String agentId,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return evaluationStore.findByAgent(agentId, limit);
    }

    /**
     * 查看评测聚合指标。
     *
     * @param agentId Agent 标识
     * @return 各维度聚合指标
     */
    @GetMapping("/stats/{agentId}")
    public Map<String, Object> getStats(@PathVariable String agentId) {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        
        for (EvaluationDimension dim : EvaluationDimension.values()) {
            EvaluationAggregate agg = evaluationStore.getAggregate(agentId, dim, 0);
            stats.put(dim.name().toLowerCase(), Map.of(
                    "avg", String.format("%.2f", agg.avg()),
                    "min", String.format("%.2f", agg.min()),
                    "max", String.format("%.2f", agg.max()),
                    "count", agg.count()
            ));
        }

        return stats;
    }

    /**
     * 提交人工评测反馈。
     *
     * @param feedback 反馈数据
     * @return 处理结果
     */
    @PostMapping("/feedback")
    public Map<String, String> submitHumanFeedback(@RequestBody Map<String, Object> feedback) {
        String evalId = (String) feedback.get("evalId");
        String agentId = (String) feedback.get("agentId");
        double score = ((Number) feedback.getOrDefault("score", 0.5)).doubleValue();
        String comment = (String) feedback.get("comment");

        log.info("[Demo] 人工反馈: evalId={} agentId={} score={} comment={}",
                evalId, agentId, score, comment);

        return Map.of(
                "status", "accepted",
                "message", "人工反馈已记录，将用于后续评测优化"
        );
    }

    /**
     * 从 TaskResult 中提取可读的输出文本。
     */
    private String extractOutput(TaskResult result) {
        if (result == null || result.result() == null) return null;
        Object response = result.result().get("response");
        if (response != null) return String.valueOf(response);
        Object output = result.result().get("output");
        if (output != null) return String.valueOf(output);
        return result.result().toString();
    }
}
