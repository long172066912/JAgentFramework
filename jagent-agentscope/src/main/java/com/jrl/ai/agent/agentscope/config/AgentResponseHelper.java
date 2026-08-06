package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Agent 通用响应构建器 — 将 TaskResult 中的公共字段（trace、tokenUsage、evaluation、optimization）
 * 标准化转换为 API 响应 Map。
 *
 * <p>任何 Agent 服务均可使用此类将执行结果统一序列化，避免在每个业务 Controller 中重复编写转换逻辑。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 1. 从 TaskResult 构建基础响应（含 trace + tokenUsage）
 * Map<String, Object> response = responseBuilder.buildBaseResponse(taskResult);
 *
 * // 2. 追加评测 + 优化建议
 * responseBuilder.enrichWithEvaluation(response, agentId);
 *
 * // 3. 合并业务数据
 * response.put("tags", myBusinessData);
 * }</pre>
 */
public class AgentResponseHelper {

    private static final Logger log = LoggerFactory.getLogger(AgentResponseHelper.class);

    private final EvaluationStore evaluationStore;
    private final OptimizationReportStore optimizationReportStore;

    /**
     * 创建 AgentResponseHelper。
     *
     * @param evaluationStore        评测结果存储（可选，null 时跳过评测查询）
     * @param optimizationReportStore 优化报告存储（可选，null 时跳过优化查询）
     */
    public AgentResponseHelper(EvaluationStore evaluationStore,
                               OptimizationReportStore optimizationReportStore) {
        this.evaluationStore = evaluationStore;
        this.optimizationReportStore = optimizationReportStore;
        log.info("[AgentResponseHelper] Initialized: evaluationStore={}, optimizationReportStore={}",
                evaluationStore != null, optimizationReportStore != null);
    }

    // ========== 核心方法 ==========

    /**
     * 从 TaskResult 构建基础响应 Map（含 trace + tokenUsage + processTime）。
     *
     * @param taskResult Agent 执行结果
     * @return 包含 trace、tokenUsage、processTime 的响应 Map
     */
    public Map<String, Object> buildBaseResponse(TaskResult taskResult) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("trace", toTraceMap(taskResult.trace()));
        response.put("tokenUsage", toTokenUsageMap(taskResult.usage()));
        response.put("processTime", taskResult.durationMs());
        return response;
    }

    /**
     * 向响应 Map 追加评测结果和优化建议。
     *
     * <p>自动从 EvaluationStore 和 OptimizationReportStore 查询最新数据。
     *
     * @param response 响应 Map（会被直接修改）
     * @param agentId  Agent 标识
     */
    public void enrichWithEvaluation(Map<String, Object> response, String agentId) {
        EvaluationResult evaluation = queryEvaluation(agentId);
        response.put("evaluation", toEvaluationMap(evaluation));

        OptimizationReport optimization = null;
        if (evaluation != null) {
            optimization = queryOptimization(agentId);
        }
        response.put("optimization", toOptimizationMap(optimization));
    }

    /**
     * 一站式构建完整响应（基础 + 评测 + 优化）。
     *
     * @param taskResult Agent 执行结果
     * @param agentId    Agent 标识
     * @return 包含 trace、tokenUsage、processTime、evaluation、optimization 的响应 Map
     */
    public Map<String, Object> buildFullResponse(TaskResult taskResult, String agentId) {
        Map<String, Object> response = buildBaseResponse(taskResult);
        enrichWithEvaluation(response, agentId);
        return response;
    }

    // ========== 查询方法 ==========

    /**
     * 查询指定 Agent 的最新评测结果。
     *
     * @param agentId Agent 标识
     * @return 最新评测结果，无数据时返回 null
     */
    public EvaluationResult queryEvaluation(String agentId) {
        if (evaluationStore == null) {
            log.debug("[AgentResponseHelper] EvaluationStore not available, skipping evaluation query");
            return null;
        }
        List<EvaluationResult> results = evaluationStore.findByAgent(agentId, 1);
        if (results.isEmpty()) {
            log.warn("[AgentResponseHelper] No evaluation found for agentId={}", agentId);
            return null;
        }
        EvaluationResult evaluation = results.getFirst();
        log.debug("[AgentResponseHelper] evaluation: evalId={} composite={}",
                evaluation.evalId(), evaluation.compositeScore());
        return evaluation;
    }

    /**
     * 查询指定 Agent 的最新优化报告。
     *
     * @param agentId Agent 标识
     * @return 最新优化报告，无数据时返回 null
     */
    public OptimizationReport queryOptimization(String agentId) {
        if (optimizationReportStore == null) {
            log.debug("[AgentResponseHelper] OptimizationReportStore not available, skipping optimization query");
            return null;
        }
        List<OptimizationReport> reports = optimizationReportStore.findByAgent(agentId, 1);
        if (reports.isEmpty()) {
            log.debug("[AgentResponseHelper] No optimization report found for agentId={}", agentId);
            return null;
        }
        OptimizationReport report = reports.getFirst();
        log.debug("[AgentResponseHelper] optimization: suggestions={}", report.suggestions().size());
        return report;
    }

    // ========== 序列化方法 ==========

    /**
     * 将 TokenUsage 转换为响应 Map。
     *
     * @param usage Token 消耗统计（null 时返回空 Map）
     * @return 包含 model、promptTokens、completionTokens、totalTokens 的 Map
     */
    public static Map<String, Object> toTokenUsageMap(TokenUsage usage) {
        if (usage == null) {
            return Map.of();
        }
        return Map.of(
                "model", usage.modelId() != null ? usage.modelId() : "",
                "promptTokens", usage.promptTokens(),
                "completionTokens", usage.completionTokens(),
                "totalTokens", usage.totalTokens()
        );
    }

    /**
     * 将 ExecutionTrace 转换为响应 Map。
     *
     * <p>业务步骤（steps）与 OTel 链路快照（traceId/analysis/spans）
     * 平铺在同一 trace 对象内，随响应一次返回。
     *
     * @param trace 执行链路（null 时返回空 Map）
     * @return 包含 steps、totalTime，以及可选的 traceId/analysis/spans 的 Map
     */
    public static Map<String, Object> toTraceMap(ExecutionTrace trace) {
        if (trace == null) {
            return Map.of();
        }
        List<Map<String, Object>> steps = trace.steps().stream()
                .map(s -> Map.<String, Object>of(
                        "name", s.name() != null ? s.name() : "",
                        "duration", s.duration(),
                        "detail", s.detail() != null ? s.detail() : ""
                ))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps", steps);
        result.put("totalTime", trace.totalTime());
        if (trace.otel() != null) {
            result.put("traceId", trace.otel().traceId());
            result.put("analysis", trace.otel().analysis());
            result.put("spans", trace.otel().spans());
        }
        return result;
    }

    /**
     * 将 EvaluationResult 转换为响应 Map。
     *
     * @param evaluation 评测结果（null 时返回空 Map）
     * @return 包含 evalId、traceId、compositeScore、dimensions 的 Map
     */
    public static Map<String, Object> toEvaluationMap(EvaluationResult evaluation) {
        if (evaluation == null) {
            return Map.of();
        }
        Map<String, Object> dimensions = new LinkedHashMap<>();
        for (var entry : evaluation.scores().entrySet()) {
            DimensionScore ds = entry.getValue();
            Map<String, Object> dimMap = new LinkedHashMap<>();
            dimMap.put("dimension", ds.dimension().name());
            dimMap.put("score", ds.score());
            dimMap.put("level", ds.level().name());
            dimMap.put("reason", ds.reason() != null ? ds.reason() : "");
            dimMap.put("metrics", ds.metrics() != null ? ds.metrics() : Map.of());
            dimensions.put(entry.getKey().name(), dimMap);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evalId", evaluation.evalId());
        result.put("traceId", evaluation.traceId() != null ? evaluation.traceId() : "");
        result.put("compositeScore", evaluation.compositeScore());
        result.put("dimensions", dimensions);
        return result;
    }

    /**
     * 将 OptimizationReport 转换为响应 Map。
     *
     * @param report 优化报告（null 时返回空 Map）
     * @return 包含 agentId、compositeScore、suggestions 的 Map
     */
    public static Map<String, Object> toOptimizationMap(OptimizationReport report) {
        if (report == null) {
            return Map.of();
        }
        List<Map<String, Object>> suggestions = report.suggestions().stream()
                .map(s -> Map.<String, Object>of(
                        "category", s.category().name(),
                        "priority", s.priority().name(),
                        "title", s.title(),
                        "content", s.content(),
                        "reason", s.reason()
                ))
                .toList();
        return Map.of(
                "agentId", report.agentId(),
                "compositeScore", report.compositeScore(),
                "suggestions", suggestions
        );
    }
}
