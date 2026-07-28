package com.jrl.ai.agent.core.evaluation;

/**
 * 评测聚合指标 — 某 Agent 某维度的统计汇总。
 *
 * @param agentId   Agent 标识
 * @param dimension 评测维度
 * @param avg       平均分
 * @param min       最低分
 * @param max       最高分
 * @param count     评测次数
 * @param windowMs  统计时间窗口（ms）
 */
public record EvaluationAggregate(
        String agentId,
        EvaluationDimension dimension,
        double avg,
        double min,
        double max,
        int count,
        long windowMs
) {
}
