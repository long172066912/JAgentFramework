package com.jrl.ai.agent.core.retrieval;

/**
 * 检索结果 — RAG 检索返回的单条文档记录。
 *
 * @see Retriever
 */
public record RetrievalResult(
        /** 结果唯一标识 */
        String id,
        /** 匹配的文档内容 */
        String content,
        /** 相关性得分（0.0 ~ 1.0，越高越相关） */
        double score,
        /** 文档扩展元数据（如来源、标题等） */
        java.util.Map<String, Object> metadata
) {
}
