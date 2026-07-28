package com.jrl.ai.agent.core.evaluation;

import java.util.Map;

/**
 * 单维度评分 — 某个评测维度的具体得分。
 *
 * @param dimension 评测维度
 * @param score     评分值（0.0 ~ 1.0）
 * @param level     评测层级（规则/LLM/人工）
 * @param reason    评分理由（可选）
 * @param metrics   评测过程中产生的指标数据（可选）
 */
public record DimensionScore(
        EvaluationDimension dimension,
        double score,
        EvaluationLevel level,
        String reason,
        Map<String, Object> metrics
) {

    /**
     * 快速创建单维度评分。
     *
     * @param dimension 评测维度
     * @param score     评分值（0.0 ~ 1.0）
     * @param level     评测层级
     * @return 维度评分实例
     */
    public static DimensionScore of(EvaluationDimension dimension, double score, EvaluationLevel level) {
        return new DimensionScore(dimension, score, level, null, Map.of());
    }

    /**
     * 带理由的评分。
     *
     * @param dimension 评测维度
     * @param score     评分值（0.0 ~ 1.0）
     * @param level     评测层级
     * @param reason    评分理由
     * @return 维度评分实例
     */
    public static DimensionScore of(EvaluationDimension dimension, double score,
                                    EvaluationLevel level, String reason) {
        return new DimensionScore(dimension, score, level, reason, Map.of());
    }
}
