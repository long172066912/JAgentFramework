package com.jrl.ai.agent.demo.tagging.model;

import java.util.Map;

/**
 * 向量检索结果 — 对应 SearchSimilar 返回的一条记录。
 *
 * @param id           记录 ID
 * @param score        相似度分数（0-1）
 * @param tagName      标签名称
 * @param category     标签类目
 * @param scalarFields 其他标量字段
 */
public record SearchResult(
        String id,
        double score,
        String tagName,
        String category,
        Map<String, Object> scalarFields
) {}
