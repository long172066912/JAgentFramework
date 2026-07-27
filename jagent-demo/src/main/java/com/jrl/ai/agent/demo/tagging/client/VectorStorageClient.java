package com.jrl.ai.agent.demo.tagging.client;

import com.jrl.ai.agent.demo.tagging.model.SearchResult;
import com.jrl.ai.agent.demo.tagging.model.TagInfo;

import java.util.List;
import java.util.Map;

/**
 * 向量存储客户端接口 — 对应向量存储交互协议设计（gRPC + Protobuf）。
 *
 * <p>生产环境由 Milvus gRPC 实现，开发/测试使用 Mock 实现。
 */
public interface VectorStorageClient {

    /**
     * 批量写入或更新向量（BatchUpsert）。
     *
     * @param collection 集合名称（如 tag_vectors）
     * @param records    向量记录列表
     * @return 写入成功数量
     */
    int batchUpsert(String collection, List<TagInfo> records);

    /**
     * 相似向量检索（SearchSimilar）。
     *
     * @param collection 集合名称
     * @param vector     查询向量
     * @param topK       返回数量上限
     * @param filter     标量过滤表达式（如 status == 1 && level == 3）
     * @param minScore   最小相似度阈值
     * @return 相似记录列表，按分数降序
     */
    List<SearchResult> searchSimilar(String collection, List<Float> vector,
                                      int topK, String filter, double minScore);

    /**
     * 批量查询向量（BatchGet）。
     *
     * @param collection 集合名称
     * @param ids        记录 ID 列表
     * @return ID → 记录映射
     */
    Map<String, TagInfo> batchGet(String collection, List<String> ids);
}
