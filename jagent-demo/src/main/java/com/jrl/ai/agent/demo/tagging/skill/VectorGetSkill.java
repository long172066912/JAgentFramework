package com.jrl.ai.agent.demo.tagging.skill;

import com.jrl.ai.agent.demo.tagging.client.VectorStorageClient;
import com.jrl.ai.agent.demo.tagging.model.TagInfo;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批量查询向量 Skill — Agent 通过此 Skill 按 ID 列表从 Milvus 查询标签数据。
 *
 * <p>对应向量存储交互协议中的 BatchGet 接口。
 *
 * <p>输入参数（通过 SkillContext.parameters）：
 * <ul>
 *   <li>{@code collection} — 集合名称（默认 tag_vectors）</li>
 *   <li>{@code ids} — 记录 ID 列表（List&lt;String&gt;）</li>
 * </ul>
 */
public class VectorGetSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(VectorGetSkill.class);

    private final VectorStorageClient vectorClient;

    public VectorGetSkill(VectorStorageClient vectorClient) {
        this.vectorClient = vectorClient;
    }

    @Override
    public String name() {
        return "vector_get";
    }

    @Override
    public String description() {
        return "根据 ID 列表从 Milvus 批量查询标签向量数据。输入参数：collection(集合名称)、ids(ID列表)。返回标签详情。";
    }

    @SuppressWarnings("unchecked")
    @Override
    public SkillResult execute(SkillContext context) {
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> params = context.parameters();
            String collection = (String) params.getOrDefault("collection", "tag_vectors");
            List<String> ids = (List<String>) params.get("ids");

            if (ids == null || ids.isEmpty()) {
                return SkillResult.failure(name(), "ID 列表不能为空", System.currentTimeMillis() - start);
            }

            Map<String, TagInfo> records = vectorClient.batchGet(collection, ids);

            String output = records.values().stream()
                    .map(t -> "  - id=%s, name=%s, category=%s, confidence=%.2f, desc=%s, keywords=%s"
                            .formatted(t.id(), t.tagName(), t.category(), t.confidence(),
                                    t.description() != null ? t.description() : "",
                                    t.keywords() != null ? String.join("/", t.keywords()) : ""))
                    .collect(Collectors.joining("\n", "查询到 %d 条标签：\n".formatted(records.size()), ""));

            log.info("[Skill:{}] get collection={}, requested={}, found={}",
                    name(), collection, ids.size(), records.size());

            return SkillResult.success(name(), output, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("[Skill:{}] failed", name(), e);
            return SkillResult.failure(name(), "批量查询失败: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
