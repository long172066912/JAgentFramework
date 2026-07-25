package com.jrl.ai.agent.core.retrieval;

import java.util.List;
import java.util.Map;

/**
 * 检索器 — RAG 检索抽象接口。
 *
 * <p>定义从知识库中检索相关文档的标准操作，
 * 具体实现可对接向量数据库、搜索引擎等。
 *
 * @see RetrievalResult
 */
public interface Retriever {

    /**
     * 根据查询检索相关文档。
     *
     * @param query 查询文本
     * @param topK  返回的最大结果数
     * @return 按相关性降序排列的检索结果列表
     */
    List<RetrievalResult> retrieve(String query, int topK);

    /**
     * 带过滤条件的检索。
     *
     * @param query   查询文本
     * @param topK    返回的最大结果数
     * @param filters 元数据过滤条件（键值对）
     * @return 符合条件的检索结果列表
     */
    List<RetrievalResult> retrieve(String query, int topK, Map<String, Object> filters);
}
