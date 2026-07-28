package com.jrl.ai.agent.demo.tagging.model;

import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.contract.TokenUsage;

import java.util.List;

/**
 * 打标结果 — AI Agent 对一条内容完成打标后的输出。
 *
 * @param contentId   内容 ID（商品/任务/动态）
 * @param contentType 内容类型（product / task / post）
 * @param tags        抽取的标签列表
 * @param embedding   内容语义向量（所有标签向量的加权平均）
 * @param usage       LLM Token 消耗统计
 * @param trace       执行链路追踪
 * @param processTime 处理耗时（ms）
 * @param evaluation  评测结果（评测系统启用时非 null）
 * @param error       错误信息（成功时为 null）
 */
public record TaggingResult(
        String contentId,
        String contentType,
        List<TagInfo> tags,
        List<Float> embedding,
        TokenUsage usage,
        ExecutionTrace trace,
        long processTime,
        EvaluationResult evaluation,
        String error
) {
    /** 兼容旧构造（无评测、无错误）。 */
    public TaggingResult(String contentId, String contentType, List<TagInfo> tags,
                         List<Float> embedding, TokenUsage usage, ExecutionTrace trace,
                         long processTime, EvaluationResult evaluation) {
        this(contentId, contentType, tags, embedding, usage, trace, processTime, evaluation, null);
    }

    /** 兼容旧构造（无评测）。 */
    public TaggingResult(String contentId, String contentType, List<TagInfo> tags,
                         List<Float> embedding, TokenUsage usage, ExecutionTrace trace, long processTime) {
        this(contentId, contentType, tags, embedding, usage, trace, processTime, null, null);
    }
}
