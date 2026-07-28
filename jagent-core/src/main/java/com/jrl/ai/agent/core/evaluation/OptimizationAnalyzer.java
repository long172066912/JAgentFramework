package com.jrl.ai.agent.core.evaluation;

/**
 * 优化分析器 — 基于评测结果生成优化建议报告。
 *
 * <p>分析 Agent 的执行度指标（评分、延迟、Token 消耗等），
 * 从四个维度给出优化建议：提示词、Skill、模型选型、Agent 编排。
 *
 * @see OptimizationReport
 * @see OptimizationSuggestion
 */
public interface OptimizationAnalyzer {

    /**
     * 分析评测结果并生成优化报告。
     *
     * @param evaluationResult 评测结果
     * @param context          评测上下文（包含输入输出、链路等）
     * @return 优化报告（含执行度指标 + 优化建议列表）
     */
    OptimizationReport analyze(EvaluationResult evaluationResult, EvaluationContext context);
}
