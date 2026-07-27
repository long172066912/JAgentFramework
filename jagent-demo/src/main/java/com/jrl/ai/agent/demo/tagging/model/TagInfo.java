package com.jrl.ai.agent.demo.tagging.model;

import java.util.List;
import java.util.Map;

/**
 * 标签信息 — 对应 Milvus tag_vectors 集合的一条记录。
 *
 * @param id           标签唯一标识（如 tag_001）
 * @param tagName      标签名称（如 "复古胶片风"）
 * @param category     标签类目（如 "视觉风格"）
 * @param level        标签层级（1/2/3 级）
 * @param status       状态（1 有效 / 0 失效）
 * @param vector       标签语义向量（768 维）
 * @param confidence   置信度（0-1）
 * @param description  标签介绍（语义描述）
 * @param keywords     标签关键词（用于搜索匹配）
 * @param extraFields  扩展标量字段
 */
public record TagInfo(
        String id,
        String tagName,
        String category,
        int level,
        int status,
        List<Float> vector,
        double confidence,
        String description,
        List<String> keywords,
        Map<String, Object> extraFields
) {
    /**
     * 创建有效标签的便捷方法。
     */
    public static TagInfo of(String id, String tagName, String category, int level,
                             List<Float> vector, double confidence, String description, List<String> keywords) {
        return new TagInfo(id, tagName, category, level, 1, vector, confidence, description, keywords, Map.of());
    }
}
