package com.jrl.ai.agent.demo.tagging.model;

import java.util.List;

/**
 * LLM 输出的标签 JSON 结构。
 */
public record LlmTagOutput(
        List<LlmTag> tags
) {
    public record LlmTag(
            String name,
            String category,
            int level,
            double confidence,
            String desc,
            List<String> keywords
    ) {}
}
