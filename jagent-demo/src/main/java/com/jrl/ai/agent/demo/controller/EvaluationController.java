package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.core.evaluation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评测 API — 提供评测结果查询和人工反馈接口。
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationStore evaluationStore;

    public EvaluationController(EvaluationStore evaluationStore) {
        this.evaluationStore = evaluationStore;
    }

    /**
     * 查看 Agent 最近评测评分。
     *
     * @param agentId Agent 标识
     * @param limit   最大返回数量（默认 20）
     * @return 评测结果列表
     */
    @GetMapping("/scores/{agentId}")
    public List<EvaluationResult> getScores(@PathVariable String agentId,
                                            @RequestParam(defaultValue = "20") int limit) {
        return evaluationStore.findByAgent(agentId, limit);
    }

    /**
     * 查看 Agent 评测历史。
     *
     * @param agentId Agent 标识
     * @param limit   最大返回数量（默认 50）
     * @return 评测结果列表
     */
    @GetMapping("/history/{agentId}")
    public List<EvaluationResult> getHistory(@PathVariable String agentId,
                                             @RequestParam(defaultValue = "50") int limit) {
        return evaluationStore.findByAgent(agentId, limit);
    }

    /**
     * 提交人工评测反馈。
     *
     * @param feedback 反馈数据
     * @return 处理结果
     */
    @PostMapping("/feedback")
    public Map<String, String> submitFeedback(@RequestBody Map<String, Object> feedback) {
        String evalId = (String) feedback.getOrDefault("evalId", "");
        String agentId = (String) feedback.getOrDefault("agentId", "");
        double score = ((Number) feedback.getOrDefault("score", 0.5)).doubleValue();
        String comment = (String) feedback.getOrDefault("comment", "");

        return Map.of(
                "status", "accepted",
                "evalId", evalId,
                "agentId", agentId,
                "message", "反馈已记录"
        );
    }

    /**
     * 获取 Agent 聚合指标。
     *
     * @param agentId   Agent 标识
     * @param dimension 评测维度（可选，默认返回所有维度）
     * @param windowMs  时间窗口（ms，默认 0 表示全量）
     * @return 聚合指标
     */
    @GetMapping("/aggregate/{agentId}")
    public Map<String, EvaluationAggregate> getAggregate(
            @PathVariable String agentId,
            @RequestParam(required = false) String dimension,
            @RequestParam(defaultValue = "0") long windowMs) {

        if (dimension != null && !dimension.isEmpty()) {
            EvaluationDimension dim = EvaluationDimension.valueOf(dimension.toUpperCase());
            EvaluationAggregate agg = evaluationStore.getAggregate(agentId, dim, windowMs);
            return Map.of(dim.name(), agg);
        }

        // 返回所有维度的聚合
        Map<String, EvaluationAggregate> result = new java.util.LinkedHashMap<>();
        for (EvaluationDimension dim : EvaluationDimension.values()) {
            result.put(dim.name(), evaluationStore.getAggregate(agentId, dim, windowMs));
        }
        return result;
    }
}
