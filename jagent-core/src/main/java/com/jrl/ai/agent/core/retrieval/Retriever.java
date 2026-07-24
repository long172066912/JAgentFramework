package com.jrl.ai.agent.core.retrieval;

import java.util.List;
import java.util.Map;

/**
 * 检索器 — RAG 检索抽象
 */
public interface Retriever {

    /**
     * 根据查询检索相关文档
     */
    List<RetrievalResult> retrieve(String query, int topK);

    /**
     * 带过滤条件的检索
     */
    List<RetrievalResult> retrieve(String query, int topK, Map<String, Object> filters);
}
