package com.jrl.ai.agent.core.retrieval;

/**
 * 检索结果
 */
public record RetrievalResult(
        String id,
        String content,
        double score,
        java.util.Map<String, Object> metadata
) {
}
