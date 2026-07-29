package com.jrl.ai.agent.demo.tagging.model;

import java.util.List;

/**
 * 打标业务结果 — 只包含打标业务相关的数据。
 *
 * <p>trace、tokenUsage、evaluation、optimization 等公共字段
 * 由框架 {@code AgentResponse<T>} 自动封装，业务层无需关心。
 *
 * @param contentId   内容 ID
 * @param contentType 内容类型（product / task / post）
 * @param tags        抽取的标签列表
 * @param embedding   内容语义向量（所有标签向量的加权平均）
 */
public record TaggingResult(
        String contentId,
        String contentType,
        List<TagInfo> tags,
        List<Float> embedding
) {}
