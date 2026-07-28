package com.jrl.ai.agent.core.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 优化报告 — 基于评测结果生成的优化建议汇总。
 *
 * <p>包含执行度指标（各维度评分 + 总分）和优化建议列表。
 *
 * @param agentId          Agent 标识
 * @param sessionId        会话 ID
 * @param compositeScore   加权总分
 * @param dimensionScores  各维度评分
 * @param executionMetrics 执行度指标（延迟、成功率、Token 消耗等）
 * @param suggestions      优化建议列表（按优先级排序）
 * @param generatedAt      报告生成时间
 */
public record OptimizationReport(
        String agentId,
        String sessionId,
        double compositeScore,
        Map<EvaluationDimension, DimensionScore> dimensionScores,
        Map<String, Object> executionMetrics,
        List<OptimizationSuggestion> suggestions,
        Instant generatedAt
) {

    /**
     * 获取指定分类的建议。
     */
    public List<OptimizationSuggestion> suggestionsFor(SuggestionCategory category) {
        return suggestions.stream()
                .filter(s -> s.category() == category)
                .toList();
    }

    /**
     * 获取高优先级建议。
     */
    public List<OptimizationSuggestion> highPrioritySuggestions() {
        return suggestions.stream()
                .filter(s -> s.priority() == OptimizationSuggestion.Priority.HIGH)
                .toList();
    }

    /**
     * 创建 Builder。
     */
    public static Builder builder(String agentId) {
        return new Builder(agentId);
    }

    public static class Builder {
        private final String agentId;
        private String sessionId;
        private double compositeScore;
        private Map<EvaluationDimension, DimensionScore> dimensionScores = Map.of();
        private Map<String, Object> executionMetrics = Map.of();
        private List<OptimizationSuggestion> suggestions = List.of();
        private Instant generatedAt = Instant.now();

        Builder(String agentId) {
            this.agentId = agentId;
        }

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder compositeScore(double score) { this.compositeScore = score; return this; }
        public Builder dimensionScores(Map<EvaluationDimension, DimensionScore> scores) { this.dimensionScores = scores; return this; }
        public Builder executionMetrics(Map<String, Object> metrics) { this.executionMetrics = metrics; return this; }
        public Builder suggestions(List<OptimizationSuggestion> suggestions) { this.suggestions = suggestions; return this; }
        public Builder generatedAt(Instant at) { this.generatedAt = at; return this; }

        public OptimizationReport build() {
            return new OptimizationReport(agentId, sessionId, compositeScore,
                    dimensionScores, executionMetrics, suggestions, generatedAt);
        }
    }
}
