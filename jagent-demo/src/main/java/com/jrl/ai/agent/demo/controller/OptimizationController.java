package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.core.evaluation.OptimizationReport;
import com.jrl.ai.agent.core.evaluation.OptimizationReportStore;
import com.jrl.ai.agent.core.evaluation.OptimizationSuggestion;
import com.jrl.ai.agent.core.evaluation.SuggestionCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 优化建议 REST 端点 — 查询 Agent 优化报告和建议。
 */
@RestController
@RequestMapping("/api/optimization")
public class OptimizationController {

    private final OptimizationReportStore reportStore;

    @Autowired
    public OptimizationController(@Autowired(required = false) OptimizationReportStore reportStore) {
        this.reportStore = reportStore;
    }

    /**
     * 获取指定 Agent 的最新优化报告。
     *
     * <pre>
     * GET /api/optimization/report/{agentId}
     * </pre>
     *
     * @param agentId Agent 标识
     * @return 最新优化报告
     */
    @GetMapping("/report/{agentId}")
    public Mono<Map<String, Object>> getLatestReport(@PathVariable String agentId) {
        return Mono.fromCallable(() -> {
            if (reportStore == null) {
                return Map.<String, Object>of("error", "Optimization system not enabled");
            }

            OptimizationReport report = reportStore.findLatest(agentId);
            if (report == null) {
                return Map.<String, Object>of("error", "No optimization report found for agent: " + agentId);
            }

            return serializeReport(report);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取指定 Agent 的优化报告历史。
     *
     * <pre>
     * GET /api/optimization/reports/{agentId}?limit=10
     * </pre>
     *
     * @param agentId Agent 标识
     * @param limit   最大返回数量（默认 10）
     * @return 优化报告列表
     */
    @GetMapping("/reports/{agentId}")
    public Mono<List<Map<String, Object>>> getReports(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "10") int limit) {
        return Mono.fromCallable(() -> {
            if (reportStore == null) {
                List<Map<String, Object>> err = new ArrayList<>();
                err.add(Map.of("error", "Optimization system not enabled"));
                return err;
            }

            return reportStore.findByAgent(agentId, limit).stream()
                    .map(this::serializeReport)
                    .collect(Collectors.<Map<String, Object>>toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取指定 Agent 的优化建议（按分类分组）。
     *
     * <pre>
     * GET /api/optimization/suggestions/{agentId}
     * </pre>
     *
     * @param agentId Agent 标识
     * @return 按分类分组的优化建议
     */
    @GetMapping("/suggestions/{agentId}")
    public Mono<Map<String, Object>> getSuggestions(@PathVariable String agentId) {
        return Mono.fromCallable(() -> {
            if (reportStore == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "Optimization system not enabled");
                return err;
            }

            OptimizationReport report = reportStore.findLatest(agentId);
            if (report == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "No optimization report found for agent: " + agentId);
                return err;
            }

            // 按分类分组
            Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (SuggestionCategory category : SuggestionCategory.values()) {
                List<OptimizationSuggestion> suggestions = report.suggestionsFor(category);
                grouped.put(category.name(), suggestions.stream()
                        .map(this::serializeSuggestion)
                        .collect(Collectors.toList()));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("agentId", agentId);
            result.put("compositeScore", String.format("%.2f", report.compositeScore()));
            result.put("suggestionsByCategory", grouped);
            result.put("highPriorityCount", report.highPrioritySuggestions().size());
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> serializeReport(OptimizationReport report) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", report.agentId());
        result.put("sessionId", report.sessionId() != null ? report.sessionId() : "");
        result.put("compositeScore", String.format("%.2f", report.compositeScore()));
        result.put("generatedAt", report.generatedAt().toString());
        result.put("executionMetrics", report.executionMetrics());

        // 各维度得分
        Map<String, String> dimensionScores = new LinkedHashMap<>();
        for (var entry : report.dimensionScores().entrySet()) {
            dimensionScores.put(entry.getKey().name(),
                    String.format("%.2f", entry.getValue().score()));
        }
        result.put("dimensionScores", dimensionScores);

        // 建议列表
        result.put("suggestions", report.suggestions().stream()
                .map(this::serializeSuggestion)
                .collect(Collectors.toList()));

        return result;
    }

    private Map<String, Object> serializeSuggestion(OptimizationSuggestion suggestion) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("category", suggestion.category().name());
        s.put("categoryDesc", suggestion.category().description());
        s.put("priority", suggestion.priority().name());
        s.put("priorityLabel", suggestion.priority().label());
        s.put("title", suggestion.title());
        s.put("content", suggestion.content());
        s.put("reason", suggestion.reason());
        return s;
    }
}
