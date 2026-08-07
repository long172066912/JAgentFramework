package com.jrl.ai.agent.demo.tagging.skill;

import com.jrl.ai.agent.demo.tagging.client.VectorStorageClient;
import com.jrl.ai.agent.demo.tagging.model.SearchResult;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 相似向量检索 Skill — Agent 通过此 Skill 在 Milvus 中检索相似标签。
 *
 * <p>对应向量存储交互协议中的 SearchSimilar 接口。
 *
 * <p>输入参数（通过 SkillContext.parameters）：
 * <ul>
 *   <li>{@code collection} — 集合名称（默认 tag_vectors）</li>
 *   <li>{@code vector} — 查询向量（List&lt;Float&gt;）</li>
 *   <li>{@code topK} — 返回数量上限（默认 10）</li>
 *   <li>{@code filter} — 标量过滤表达式（可选）</li>
 *   <li>{@code minScore} — 最小相似度阈值（默认 0.0）</li>
 * </ul>
 */
public class VectorSearchSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchSkill.class);

    private final VectorStorageClient vectorClient;

    public VectorSearchSkill(VectorStorageClient vectorClient) {
        this.vectorClient = vectorClient;
    }

    @Override
    public String name() {
        return "vector_search";
    }

    @Override
    public String description() {
        return "根据查询向量在 Milvus 中检索语义相似的标签。输入参数：collection(集合名称)、vector(查询向量)、topK(返回数量)、filter(过滤表达式)、minScore(最小相似度)。返回相似标签列表。";
    }

    @Override
    public Set<String> applicableAgents() {
        // 静态挂载边界：向量检索是打标场景专属能力，只对 tagger 可见
        return Set.of("tagger");
    }

    @Override
    public List<String> keywords() {
        return List.of("标签去重", "语义相似检索", "已有标签查询");
    }

    @Override
    public String whenToUse() {
        return "需要在新建标签前查重，确认向量库中是否已存在语义相似的标签时调用";
    }

    @Override
    public String whenNotToUse() {
        return "任务只要求抽取标签并直接输出 JSON 结果时，不要调用本工具";
    }

    @SuppressWarnings("unchecked")
    @Override
    public SkillResult execute(SkillContext context) {
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> params = context.parameters();
            String collection = (String) params.getOrDefault("collection", "tag_vectors");
            List<Float> vector = (List<Float>) params.get("vector");
            int topK = params.containsKey("topK") ? ((Number) params.get("topK")).intValue() : 10;
            String filter = (String) params.getOrDefault("filter", "status == 1");
            double minScore = params.containsKey("minScore") ? ((Number) params.get("minScore")).doubleValue() : 0.0;

            if (vector == null || vector.isEmpty()) {
                return SkillResult.failure(name(), "查询向量不能为空", System.currentTimeMillis() - start);
            }

            List<SearchResult> results = vectorClient.searchSimilar(collection, vector, topK, filter, minScore);

            String output = results.stream()
                    .map(r -> "  - id=%s, name=%s, score=%.4f".formatted(r.id(), r.tagName(), r.score()))
                    .collect(Collectors.joining("\n", "检索到 %d 条相似标签：\n".formatted(results.size()), ""));

            log.info("[Skill:{}] search collection={}, topK={}, found={}", name(), collection, topK, results.size());

            return SkillResult.success(name(), output, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("[Skill:{}] failed", name(), e);
            return SkillResult.failure(name(), "向量检索失败: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
