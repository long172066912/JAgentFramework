package com.jrl.ai.agent.demo.tagging.client;

import com.jrl.ai.agent.demo.tagging.model.SearchResult;
import com.jrl.ai.agent.demo.tagging.model.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock 向量存储客户端 — 内存实现，用于开发测试。
 *
 * <p>生产环境替换为 Milvus gRPC 实现。
 */
@Component
public class MockVectorStorageClient implements VectorStorageClient {

    private static final Logger log = LoggerFactory.getLogger(MockVectorStorageClient.class);

    /** collection → (id → TagInfo) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, TagInfo>> storage =
            new ConcurrentHashMap<>();

    @Override
    public int batchUpsert(String collection, List<TagInfo> records) {
        ConcurrentHashMap<String, TagInfo> col = storage.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
        for (TagInfo record : records) {
            col.put(record.id(), record);
            log.debug("[MockMilvus] upsert collection={} id={} tag={}", collection, record.id(), record.tagName());
        }
        log.info("[MockMilvus] BatchUpsert collection={}, count={}", collection, records.size());
        return records.size();
    }

    @Override
    public List<SearchResult> searchSimilar(String collection, List<Float> vector,
                                             int topK, String filter, double minScore) {
        ConcurrentHashMap<String, TagInfo> col = storage.get(collection);
        if (col == null || col.isEmpty()) {
            log.info("[MockMilvus] SearchSimilar collection={}, empty collection", collection);
            return List.of();
        }

        // 简单余弦相似度计算
        List<SearchResult> results = col.values().stream()
                .filter(t -> t.status() == 1) // 只检索有效标签
                .map(t -> {
                    double score = cosineSimilarity(vector, t.vector());
                    return new SearchResult(t.id(), score, t.tagName(), t.category(), Map.of());
                })
                .filter(r -> r.score() >= minScore)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .collect(Collectors.toList());

        log.info("[MockMilvus] SearchSimilar collection={}, topK={}, found={}", collection, topK, results.size());
        return results;
    }

    @Override
    public Map<String, TagInfo> batchGet(String collection, List<String> ids) {
        ConcurrentHashMap<String, TagInfo> col = storage.get(collection);
        if (col == null) {
            return Map.of();
        }
        Map<String, TagInfo> result = new LinkedHashMap<>();
        for (String id : ids) {
            TagInfo info = col.get(id);
            if (info != null) {
                result.put(id, info);
            }
        }
        log.info("[MockMilvus] BatchGet collection={}, requested={}, found={}", collection, ids.size(), result.size());
        return result;
    }

    /**
     * 余弦相似度计算。
     */
    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            float va = a.get(i);
            float vb = b.get(i);
            dotProduct += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dotProduct / denom;
    }

    /**
     * 获取当前存储的标签总数（用于测试/监控）。
     */
    public int totalTags() {
        return storage.values().stream().mapToInt(ConcurrentHashMap::size).sum();
    }

    /**
     * 清空所有数据（用于测试）。
     */
    public void clear() {
        storage.clear();
    }
}
