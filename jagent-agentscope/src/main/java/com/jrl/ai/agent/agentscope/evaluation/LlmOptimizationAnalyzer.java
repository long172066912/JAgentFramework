package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 LLM 的优化分析器 — 调用大模型分析评测结果并生成优化建议。
 *
 * <p>从四个维度给出建议：
 * <ul>
 *   <li>提示词优化 — 改进系统提示词、用户提示词</li>
 *   <li>Skill 优化 — 新增/调整/替换 Skill</li>
 *   <li>模型推荐 — 推荐更合适的大模型</li>
 *   <li>Agent 与步骤优化 — 改进编排流程</li>
 * </ul>
 *
 * <p>如果 LLM 调用失败，回退到 {@link RuleBasedOptimizationAnalyzer} 生成基础建议。
 */
public class LlmOptimizationAnalyzer implements OptimizationAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LlmOptimizationAnalyzer.class);

    private final Model model;
    private final RuleBasedOptimizationAnalyzer fallback;

    public LlmOptimizationAnalyzer(Model model) {
        this.model = model;
        this.fallback = new RuleBasedOptimizationAnalyzer();
    }

    @Override
    public OptimizationReport analyze(EvaluationResult evalResult, EvaluationContext context) {
        try {
            String prompt = buildPrompt(evalResult, context);
            String response = model.call(List.of(ChatMessage.user(prompt)));

            List<OptimizationSuggestion> suggestions = parseSuggestions(response);
            if (suggestions.isEmpty()) {
                log.warn("[Optimization] LLM 返回为空，回退到规则分析");
                suggestions = fallback.analyze(evalResult, context).suggestions();
            }

            // 补充规则建议（LLM 可能遗漏的客观指标）
            List<OptimizationSuggestion> ruleSuggestions = fallback.analyze(evalResult, context).suggestions();
            suggestions = mergeSuggestions(suggestions, ruleSuggestions);

            Map<String, Object> metrics = buildExecutionMetrics(evalResult, context);

            return OptimizationReport.builder(evalResult.agentId())
                    .sessionId(evalResult.sessionId())
                    .compositeScore(evalResult.compositeScore())
                    .dimensionScores(evalResult.scores())
                    .executionMetrics(metrics)
                    .suggestions(suggestions)
                    .build();

        } catch (Exception e) {
            log.warn("[Optimization] LLM 分析失败，回退到规则分析: {}", e.getMessage());
            return fallback.analyze(evalResult, context);
        }
    }

    private String buildPrompt(EvaluationResult evalResult, EvaluationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 AI Agent 优化专家。请根据以下评测结果，给出具体的优化建议。\n\n");

        sb.append("## Agent 信息\n");
        sb.append("- Agent ID: ").append(evalResult.agentId()).append("\n");

        sb.append("\n## 评测得分\n");
        sb.append(String.format("- 综合得分: %.2f\n", evalResult.compositeScore()));
        for (var entry : evalResult.scores().entrySet()) {
            DimensionScore ds = entry.getValue();
            sb.append(String.format("- %s: %.2f (%s)\n", entry.getKey().name(), ds.score(), ds.reason()));
        }

        if (context.trace() != null) {
            sb.append("\n## 执行链路\n");
            sb.append(String.format("- 总耗时: %dms\n", context.trace().totalTime()));
            for (var step : context.trace().steps()) {
                sb.append(String.format("- %s: %dms (%s)\n", step.name(), step.duration(), step.detail()));
            }
        }

        if (context.input() != null) {
            sb.append("\n## 用户输入\n");
            sb.append(truncate(context.input(), 500)).append("\n");
        }

        if (context.output() != null) {
            sb.append("\n## Agent 输出\n");
            sb.append(truncate(context.output(), 1000)).append("\n");
        }

        sb.append("\n## 请从以下四个维度给出优化建议：\n");
        sb.append("1. **提示词优化 (PROMPT)** — 如何改进系统提示词或用户提示词\n");
        sb.append("2. **Skill 优化 (SKILL)** — 是否需要新增/调整/替换 Skill\n");
        sb.append("3. **模型推荐 (MODEL)** — 是否有更合适的大模型推荐\n");
        sb.append("4. **Agent 与步骤优化 (AGENT_STEP)** — 编排流程如何改进\n\n");

        sb.append("请按以下 JSON 格式返回（每个维度至少 1 条建议）：\n");
        sb.append("```json\n");
        sb.append("[\n");
        sb.append("  {\"category\": \"PROMPT\", \"priority\": \"HIGH|MEDIUM|LOW\", \"title\": \"建议标题\", \"content\": \"具体建议内容\", \"reason\": \"依据\"},\n");
        sb.append("  {\"category\": \"SKILL\", \"priority\": \"...\", \"title\": \"...\", \"content\": \"...\", \"reason\": \"...\"},\n");
        sb.append("  {\"category\": \"MODEL\", \"priority\": \"...\", \"title\": \"...\", \"content\": \"...\", \"reason\": \"...\"},\n");
        sb.append("  {\"category\": \"AGENT_STEP\", \"priority\": \"...\", \"title\": \"...\", \"content\": \"...\", \"reason\": \"...\"}\n");
        sb.append("]\n");
        sb.append("```\n");

        return sb.toString();
    }

    private List<OptimizationSuggestion> parseSuggestions(String response) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // 提取 JSON 数组
        Pattern jsonPattern = Pattern.compile("\\[\\s*\\{.*}\\s*]", Pattern.DOTALL);
        Matcher jsonMatcher = jsonPattern.matcher(response);
        if (!jsonMatcher.find()) {
            return suggestions;
        }

        String json = jsonMatcher.group();
        // 简单解析 JSON 对象
        Pattern objPattern = Pattern.compile("\\{[^{}]+}");
        Matcher objMatcher = objPattern.matcher(json);

        while (objMatcher.find()) {
            String obj = objMatcher.group();
            try {
                String category = extractField(obj, "category");
                String priority = extractField(obj, "priority");
                String title = extractField(obj, "title");
                String content = extractField(obj, "content");
                String reason = extractField(obj, "reason");

                if (category == null || title == null) continue;

                SuggestionCategory cat;
                try {
                    cat = SuggestionCategory.valueOf(category.toUpperCase());
                } catch (IllegalArgumentException e) {
                    cat = SuggestionCategory.AGENT_STEP;
                }

                OptimizationSuggestion.Priority pri = switch (priority != null ? priority.toUpperCase() : "MEDIUM") {
                    case "HIGH" -> OptimizationSuggestion.Priority.HIGH;
                    case "LOW" -> OptimizationSuggestion.Priority.LOW;
                    default -> OptimizationSuggestion.Priority.MEDIUM;
                };

                suggestions.add(new OptimizationSuggestion(cat, pri,
                        title != null ? title : "",
                        content != null ? content : "",
                        reason != null ? reason : ""));
            } catch (Exception e) {
                log.debug("[Optimization] 解析建议失败: {}", obj, e);
            }
        }

        return suggestions;
    }

    private String extractField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private List<OptimizationSuggestion> mergeSuggestions(
            List<OptimizationSuggestion> llmSuggestions,
            List<OptimizationSuggestion> ruleSuggestions) {

        // 按分类合并，LLM 建议优先，规则建议补充
        Set<SuggestionCategory> coveredCategories = new HashSet<>();
        for (OptimizationSuggestion s : llmSuggestions) {
            coveredCategories.add(s.category());
        }

        List<OptimizationSuggestion> merged = new ArrayList<>(llmSuggestions);
        for (OptimizationSuggestion s : ruleSuggestions) {
            if (!coveredCategories.contains(s.category())) {
                merged.add(s);
                coveredCategories.add(s.category());
            }
        }

        // 按优先级排序
        merged.sort(Comparator.comparing(s -> s.priority().ordinal()));
        return merged;
    }

    private Map<String, Object> buildExecutionMetrics(EvaluationResult evalResult, EvaluationContext context) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("compositeScore", String.format("%.2f", evalResult.compositeScore()));

        if (context.trace() != null) {
            metrics.put("totalTimeMs", context.trace().totalTime());
            metrics.put("stepCount", context.trace().steps().size());
        }

        // 各维度得分
        for (var entry : evalResult.scores().entrySet()) {
            metrics.put(entry.getKey().name().toLowerCase() + "Score",
                    String.format("%.2f", entry.getValue().score()));
        }

        return metrics;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
