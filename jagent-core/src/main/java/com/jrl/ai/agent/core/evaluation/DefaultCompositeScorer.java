package com.jrl.ai.agent.core.evaluation;

import java.util.EnumMap;
import java.util.Map;

/**
 * 默认复合评分器 — 基于可配权重的加权平均。
 *
 * <p>五维权重默认：
 * <ul>
 *   <li>INTELLIGENCE: 0.3</li>
 *   <li>PERFORMANCE: 0.15</li>
 *   <li>RELIABILITY: 0.2</li>
 *   <li>SAFETY: 0.2</li>
 *   <li>EXPERIENCE: 0.15</li>
 * </ul>
 *
 * <p>仅对实际有评分的维度按权重归一化后计算总分。
 */
public class DefaultCompositeScorer implements CompositeScorer {

    private final Map<EvaluationDimension, Double> weights;

    /**
     * 使用默认权重创建。
     */
    public DefaultCompositeScorer() {
        this.weights = defaultWeights();
    }

    /**
     * 使用自定义权重创建。
     *
     * @param weights 各维度权重
     */
    public DefaultCompositeScorer(Map<EvaluationDimension, Double> weights) {
        this.weights = new EnumMap<>(weights);
    }

    @Override
    public double compute(Map<EvaluationDimension, DimensionScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (var entry : scores.entrySet()) {
            EvaluationDimension dim = entry.getKey();
            DimensionScore score = entry.getValue();
            double weight = weights.getOrDefault(dim, 0.0);
            weightedSum += score.score() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return 0.0;
        }

        return weightedSum / totalWeight;
    }

    /**
     * 获取当前权重配置。
     *
     * @return 各维度权重
     */
    public Map<EvaluationDimension, Double> getWeights() {
        return Map.copyOf(weights);
    }

    /**
     * 默认五维权重。
     */
    public static Map<EvaluationDimension, Double> defaultWeights() {
        Map<EvaluationDimension, Double> w = new EnumMap<>(EvaluationDimension.class);
        w.put(EvaluationDimension.INTELLIGENCE, 0.3);
        w.put(EvaluationDimension.PERFORMANCE, 0.15);
        w.put(EvaluationDimension.RELIABILITY, 0.2);
        w.put(EvaluationDimension.SAFETY, 0.2);
        w.put(EvaluationDimension.EXPERIENCE, 0.15);
        return w;
    }
}
