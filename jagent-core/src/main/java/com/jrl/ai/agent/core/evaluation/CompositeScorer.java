package com.jrl.ai.agent.core.evaluation;

import java.util.Map;

/**
 * 复合评分器 — 将多维度评分汇总为加权总分。
 *
 * <p>默认实现 {@link DefaultCompositeScorer} 使用可配权重，
 * 用户可实现此接口自定义评分策略。
 */
public interface CompositeScorer {

    /**
     * 计算加权总分。
     *
     * @param scores 各维度评分
     * @return 加权总分（0.0 ~ 1.0）
     */
    double compute(Map<EvaluationDimension, DimensionScore> scores);
}
