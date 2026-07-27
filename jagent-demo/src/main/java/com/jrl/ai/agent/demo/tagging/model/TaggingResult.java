package com.jrl.ai.agent.demo.tagging.model;

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
 */
public record TaggingResult(
        String contentId,
        String contentType,
        List<TagInfo> tags,
        List<Float> embedding,
        TokenUsage usage,
        ExecutionTrace trace,
        long processTime
) {}
