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
import java.util.Set;

/**
 * 向量写入 Skill — Agent 通过此 Skill 将标签向量写入 Milvus。
 *
 * <p>对应向量存储交互协议中的 BatchUpsert 接口。
 *
 * <p>输入参数（通过 SkillContext.parameters）：
 * <ul>
 *   <li>{@code collection} — 集合名称（默认 tag_vectors）</li>
 *   <li>{@code tags} — 标签列表（List&lt;TagInfo&gt;）</li>
 * </ul>
 */
public class VectorUpsertSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(VectorUpsertSkill.class);

    private final VectorStorageClient vectorClient;

    public VectorUpsertSkill(VectorStorageClient vectorClient) {
        this.vectorClient = vectorClient;
    }

    @Override
    public String name() {
        return "vector_upsert";
    }

    @Override
    public String description() {
        return "批量写入或更新向量到 Milvus。输入参数：collection(集合名称)、tags(标签列表)。返回写入成功数量。";
    }

    @Override
    public Set<String> applicableAgents() {
        // 静态挂载边界：向量写入是打标场景专属能力，只对 tagger 可见
        return Set.of("tagger");
    }

    @Override
    public boolean canInvoke(SkillContext context) {
        // 动态调用边界：写操作要求必须携带非空 tags 参数，否则拒绝执行
        Object tags = context.parameters().get("tags");
        return tags instanceof List<?> list && !list.isEmpty();
    }

    @Override
    public List<String> keywords() {
        return List.of("标签写入", "向量入库", "批量保存");
    }

    @Override
    public String whenToUse() {
        return "标签抽取已完成、且任务明确要求将新标签持久化写入向量库时才调用";
    }

    @Override
    public String whenNotToUse() {
        return "任务只要求输出抽取结果、或标签尚未抽取完成时，不要调用本工具";
    }

    @Override
    public double priority() {
        return 0.7;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SkillResult execute(SkillContext context) {
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> params = context.parameters();
            String collection = (String) params.getOrDefault("collection", "tag_vectors");
            List<TagInfo> tags = (List<TagInfo>) params.get("tags");

            if (tags == null || tags.isEmpty()) {
                return SkillResult.failure(name(), "标签列表为空", System.currentTimeMillis() - start);
            }

            int count = vectorClient.batchUpsert(collection, tags);
            log.info("[Skill:{}] upsert {} tags to collection={}", name(), count, collection);

            return SkillResult.success(name(),
                    "成功写入 %d 条标签到集合 %s".formatted(count, collection),
                    System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("[Skill:{}] failed", name(), e);
            return SkillResult.failure(name(), "向量写入失败: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
