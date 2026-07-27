package com.jrl.ai.agent.agentscope.retrieval;

import com.jrl.ai.agent.core.retrieval.RetrievalResult;
import com.jrl.ai.agent.core.retrieval.Retriever;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * AgentScope 检索器 — 桥接 jagent-core {@link Retriever} 与 AgentScope {@link Knowledge}。
 *
 * <p>将 AgentScope 的 Knowledge 接口返回的 Document 列表转换为 jagent 的 RetrievalResult 列表。
 */
public class AgentScopeRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeRetriever.class);

    private final Knowledge knowledge;

    /**
     * 创建 AgentScope 检索器。
     *
     * @param knowledge AgentScope 知识源
     */
    public AgentScopeRetriever(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        return retrieve(query, topK, Map.of());
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK, Map<String, Object> filters) {
        log.debug("检索: query={}, topK={}, filters={}", query, topK, filters);

        try {
            RetrieveConfig config = RetrieveConfig.builder()
                    .limit(topK)
                    .build();

            List<Document> docs = knowledge.retrieve(query, config).block();
            if (docs == null) {
                return List.of();
            }

            List<RetrievalResult> results = docs.stream()
                    .map(doc -> {
                        String id = doc.getId() != null ? doc.getId() : "doc_" + doc.hashCode();
                        String content = doc.getMetadata() != null && doc.getMetadata().getContentText() != null
                                ? doc.getMetadata().getContentText() : "";
                        double score = doc.getScore() != null && doc.getScore() > 0 ? doc.getScore() : 0.5;
                        Map<String, Object> metadata = doc.getMetadata() != null && doc.getMetadata().getPayload() != null
                                ? doc.getMetadata().getPayload() : Map.of();
                        return new RetrievalResult(id, content, score, metadata);
                    })
                    .toList();

            log.info("检索完成: query={}, found={}", query, results.size());
            return results;

        } catch (Exception e) {
            log.error("检索失败: query={}", query, e);
            return List.of();
        }
    }
}
