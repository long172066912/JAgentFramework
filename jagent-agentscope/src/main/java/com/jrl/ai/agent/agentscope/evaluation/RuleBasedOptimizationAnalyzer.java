package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.evaluation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于规则的优化分析器 — 根据评测分数和阈值生成基础优化建议。
 *
 * <p>作为 {@link LlmOptimizationAnalyzer} 的降级方案，
 * 当 LLM 不可用时提供基本的优化建议。
 */
public class RuleBasedOptimizationAnalyzer implements OptimizationAnalyzer {

    /** 各维度低分阈值 — 低于此值给出优化建议 */
    private static final double LOW_SCORE_THRESHOLD = 0.6;
    /** 各维度中等阈值 — 低于此值给出改进建议 */
    private static final double MEDIUM_SCORE_THRESHOLD = 0.8;
    /** 延迟高阈值（ms） */
    private static final long HIGH_LATENCY_THRESHOLD = 10000;

    @Override
    public OptimizationReport analyze(EvaluationResult evalResult, EvaluationContext context) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // 1. 提示词优化建议（基于得分）
        suggestions.addAll(analyzePromptSuggestions(evalResult));

        // 2. Skill 优化建议
        suggestions.addAll(analyzeSkillSuggestions(evalResult, context));

        // 3. 模型推荐建议
        suggestions.addAll(analyzeModelSuggestions(evalResult, context));

        // 4. Agent 与步骤优化建议
        suggestions.addAll(analyzeAgentStepSuggestions(evalResult, context));

        // 按优先级排序
        suggestions.sort((a, b) -> a.priority().ordinal() - b.priority().ordinal());

        Map<String, Object> metrics = buildExecutionMetrics(evalResult, context);

        return OptimizationReport.builder(evalResult.agentId())
                .sessionId(evalResult.sessionId())
                .compositeScore(evalResult.compositeScore())
                .dimensionScores(evalResult.scores())
                .executionMetrics(metrics)
                .suggestions(suggestions)
                .build();
    }

    private List<OptimizationSuggestion> analyzePromptSuggestions(EvaluationResult evalResult) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        DimensionScore intelligenceScore = evalResult.scores().get(EvaluationDimension.INTELLIGENCE);
        DimensionScore experienceScore = evalResult.scores().get(EvaluationDimension.EXPERIENCE);

        if (intelligenceScore != null && intelligenceScore.score() < LOW_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.high(
                    SuggestionCategory.PROMPT,
                    "提升输出智能",
                    "在系统提示词中增加输出格式约束和示例，明确任务目标和边界条件",
                    String.format("智能维度得分仅 %.2f，%s", intelligenceScore.score(), intelligenceScore.reason())
            ));
        } else if (intelligenceScore != null && intelligenceScore.score() < MEDIUM_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.medium(
                    SuggestionCategory.PROMPT,
                    "优化提示词细节",
                    "补充更多业务上下文，增加 Few-shot 示例提升输出一致性",
                    String.format("智能维度得分 %.2f，仍有提升空间", intelligenceScore.score())
            ));
        }

        if (experienceScore != null && experienceScore.score() < LOW_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.high(
                    SuggestionCategory.PROMPT,
                    "增强用户体验",
                    "在提示词中明确用户意图，减少无关信息输出，提升交互质量",
                    String.format("体验维度得分仅 %.2f，%s", experienceScore.score(), experienceScore.reason())
            ));
        }

        return suggestions;
    }

    private List<OptimizationSuggestion> analyzeSkillSuggestions(EvaluationResult evalResult, EvaluationContext context) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        DimensionScore intelligenceScore = evalResult.scores().get(EvaluationDimension.INTELLIGENCE);

        if (intelligenceScore != null && intelligenceScore.score() < LOW_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.high(
                    SuggestionCategory.SKILL,
                    "补充缺失 Skill",
                    "Agent 输出不完整或质量不足，建议增加数据检索、信息补全等 Skill 能力",
                    String.format("智能维度得分仅 %.2f，%s", intelligenceScore.score(), intelligenceScore.reason())
            ));
        } else if (intelligenceScore != null && intelligenceScore.score() < MEDIUM_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.medium(
                    SuggestionCategory.SKILL,
                    "优化现有 Skill",
                    "调整 Skill 参数或增加后置处理 Skill，提升输出质量",
                    String.format("智能维度得分 %.2f", intelligenceScore.score())
            ));
        }

        DimensionScore safetyScore = evalResult.scores().get(EvaluationDimension.SAFETY);
        if (safetyScore != null && safetyScore.score() < MEDIUM_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.high(
                    SuggestionCategory.SKILL,
                    "增强安全校验 Skill",
                    "增加输入/输出安全过滤 Skill，防止敏感信息泄露",
                    String.format("安全性得分 %.2f，%s", safetyScore.score(), safetyScore.reason())
            ));
        }

        return suggestions;
    }

    private List<OptimizationSuggestion> analyzeModelSuggestions(EvaluationResult evalResult, EvaluationContext context) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        DimensionScore intelligenceScore = evalResult.scores().get(EvaluationDimension.INTELLIGENCE);

        if (intelligenceScore != null && intelligenceScore.score() < LOW_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.high(
                    SuggestionCategory.MODEL,
                    "升级大模型",
                    "当前模型输出质量不足，建议升级到更强大的模型（如 qwen-max、gpt-4o）",
                    String.format("智能维度得分仅 %.2f，%s", intelligenceScore.score(), intelligenceScore.reason())
            ));
        } else if (intelligenceScore != null && intelligenceScore.score() < MEDIUM_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.medium(
                    SuggestionCategory.MODEL,
                    "尝试更优模型",
                    "可尝试同系列更大参数模型，或切换至专业领域模型",
                    String.format("智能维度得分 %.2f", intelligenceScore.score())
            ));
        }

        // 延迟过高时建议换更快的模型
        if (context.trace() != null && context.trace().totalTime() > HIGH_LATENCY_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.medium(
                    SuggestionCategory.MODEL,
                    "优化响应延迟",
                    "当前延迟较高，建议换用推理速度更快的模型，或启用流式输出",
                    String.format("总耗时 %dms，超过阈值 %dms", context.trace().totalTime(), HIGH_LATENCY_THRESHOLD)
            ));
        }

        return suggestions;
    }

    private List<OptimizationSuggestion> analyzeAgentStepSuggestions(EvaluationResult evalResult, EvaluationContext context) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        if (context.trace() != null) {
            int stepCount = context.trace().steps().size();
            long totalTime = context.trace().totalTime();

            // 步骤过多
            if (stepCount > 5) {
                suggestions.add(OptimizationSuggestion.medium(
                        SuggestionCategory.AGENT_STEP,
                        "精简执行步骤",
                        "当前步骤较多，考虑合并可并行的步骤，减少串行依赖",
                        String.format("共 %d 个步骤，总耗时 %dms", stepCount, totalTime)
                ));
            }

            // 单步骤耗时过长
            for (var step : context.trace().steps()) {
                if (step.duration() > HIGH_LATENCY_THRESHOLD / 2) {
                    suggestions.add(OptimizationSuggestion.medium(
                            SuggestionCategory.AGENT_STEP,
                            "优化慢步骤: " + step.name(),
                            "该步骤耗时较长，考虑异步化或缓存优化",
                            String.format("步骤 %s 耗时 %dms", step.name(), step.duration())
                    ));
                }
            }
        }

        DimensionScore performanceScore = evalResult.scores().get(EvaluationDimension.PERFORMANCE);
        if (performanceScore != null && performanceScore.score() < LOW_SCORE_THRESHOLD) {
            suggestions.add(OptimizationSuggestion.high(
                    SuggestionCategory.AGENT_STEP,
                    "提升执行性能",
                    "优化 Agent 编排逻辑，减少冗余调用，启用并行执行",
                    String.format("性能维度得分仅 %.2f，%s", performanceScore.score(), performanceScore.reason())
            ));
        }

        return suggestions;
    }

    private Map<String, Object> buildExecutionMetrics(EvaluationResult evalResult, EvaluationContext context) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("compositeScore", String.format("%.2f", evalResult.compositeScore()));

        if (context.trace() != null) {
            metrics.put("totalTimeMs", context.trace().totalTime());
            metrics.put("stepCount", context.trace().steps().size());
        }

        for (var entry : evalResult.scores().entrySet()) {
            metrics.put(entry.getKey().name().toLowerCase() + "Score",
                    String.format("%.2f", entry.getValue().score()));
        }

        return metrics;
    }
}
