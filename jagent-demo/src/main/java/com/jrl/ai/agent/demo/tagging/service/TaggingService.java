package com.jrl.ai.agent.demo.tagging.service;

import com.jrl.ai.agent.agentscope.config.AgentExecutor;
import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import com.jrl.ai.agent.core.task.AgentResponse;
import com.jrl.ai.agent.demo.tagging.client.VectorStorageClient;
import com.jrl.ai.agent.demo.tagging.model.*;
import com.jrl.ai.agent.demo.tagging.skill.CategoryLevelSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能打标服务 — 只关心打标业务逻辑。
 *
 * <p>trace、tokenUsage、evaluation、optimization 等公共能力
 * 由 {@link AgentExecutor} 自动处理，本服务无需关心。
 *
 * <p>业务流程：内容输入 → LLM 抽取标签 → 生成向量 → 写入 Milvus
 */
@Service
public class TaggingService {

    private static final Logger log = LoggerFactory.getLogger(TaggingService.class);

    private static final int VECTOR_DIM = 768;
    private static final String TAG_COLLECTION = "tag_vectors";
    private static final int DEFAULT_TAG_COUNT = 5;
    private static final int MAX_TAG_RETRIES = 3;
    private static final Pattern TAG_PATTERN =
            Pattern.compile("\\[TAG\\]\\s*name=(.+?),\\s*category=(.+?),\\s*confidence=([\\d.]+),\\s*desc=(.+?),\\s*keywords=(.+)");

    private final AgentExecutor agentExecutor;
    private final AgentFactory agentFactory;
    private final VectorStorageClient vectorClient;
    private final CategoryLevelSkill categoryLevelSkill;

    public TaggingService(AgentExecutor agentExecutor, AgentFactory agentFactory, 
                          VectorStorageClient vectorClient, CategoryLevelSkill categoryLevelSkill) {
        this.agentExecutor = agentExecutor;
        this.agentFactory = agentFactory;
        this.vectorClient = vectorClient;
        this.categoryLevelSkill = categoryLevelSkill;
    }

    /**
     * 对内容执行智能打标（默认 5 个标签）。
     */
    public AgentResponse<TaggingResult> tag(String contentId, String contentType, String contentText) {
        return tag(contentId, contentType, contentText, DEFAULT_TAG_COUNT);
    }

    /**
     * 对内容执行智能打标。
     *
     * <p>业务只关心：标签抽取 + 向量生成 + Milvus 写入。
     * trace/tokenUsage/evaluation/optimization 由 AgentExecutor 自动处理。
     */
    public AgentResponse<TaggingResult> tag(String contentId, String contentType,
                                             String contentText, int requiredTagCount) {
        log.info("[Tagging] start contentId={} type={}", contentId, contentType);

        // 1. 渲染提示词模板（从 application.yml 配置读取）
        Map<String, Object> variables = Map.of(
                "contentType", contentType,
                "contentText", contentText,
                "requiredTagCount", requiredTagCount
        );
        String prompt = agentFactory.renderPrompt("tagger", variables);
        ChatMessage input = ChatMessage.user(prompt);
        AgentContext context = AgentContext.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId("tagging-system")
                .build();

        // AgentExecutor.execute + map：业务链式处理，公共字段全自动
        return agentExecutor.execute(
                "tagger", input, context,
                (taskResult, traceBuilder) -> {
                    String output = (String) taskResult.result().getOrDefault("response", "");
                    List<TagInfo> tags = parseTags(output, contentId);
                    log.info("[Tagging] parsed {} tags from LLM output", tags.size());
                    return tags;
                }
        ).map(tags -> {
            // 2. 以下都是业务逻辑：向量生成 + Milvus 写入
            for (int i = 0; i < tags.size(); i++) {
                TagInfo tag = tags.get(i);
                List<Float> vector = generateEmbedding(tag.tagName());
                tags.set(i, new TagInfo(
                        tag.id(), tag.tagName(), tag.category(), tag.level(),
                        tag.status(), vector, tag.confidence(), tag.description(),
                        tag.keywords(), tag.extraFields()
                ));
            }
            int upserted = vectorClient.batchUpsert(TAG_COLLECTION, tags);
            log.info("[Tagging] upserted {} tags for contentId={}", upserted, contentId);
            List<Float> contentEmbedding = computeContentEmbedding(tags);
            return new TaggingResult(contentId, contentType, tags, contentEmbedding);
        });
    }

    /**
     * 查询已存在的标签。
     */
    public Map<String, TagInfo> getTagsByIds(List<String> ids) {
        return vectorClient.batchGet(TAG_COLLECTION, ids);
    }

    /**
     * 相似标签检索。
     */
    public List<SearchResult> searchSimilarTags(List<Float> vector, int topK, double minScore) {
        return vectorClient.searchSimilar(TAG_COLLECTION, vector, topK, "status == 1", minScore);
    }

    // ========== 业务私有方法 ==========

    private List<TagInfo> parseTags(String llmOutput, String contentId) {
        List<TagInfo> tags = new ArrayList<>();
        Matcher matcher = TAG_PATTERN.matcher(llmOutput);
        int index = 0;
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String category = matcher.group(2).trim();
            double confidence = Double.parseDouble(matcher.group(3).trim());
            String description = matcher.group(4).trim();
            List<String> keywords = Arrays.stream(matcher.group(5).trim().split("/"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            String tagId = "tag_%s_%d".formatted(contentId, index++);
            int level = inferLevel(category);
            tags.add(TagInfo.of(tagId, name, category, level, List.of(), confidence, description, keywords));
        }
        return tags;
    }

    /**
     * 使用 CategoryLevelSkill 推断标签层级。
     */
    private int inferLevel(String category) {
        SkillResult result = categoryLevelSkill.execute(new SkillContext(
                categoryLevelSkill.name(),
                category,
                null,
                Map.of("category", category)
        ));
        try {
            return Integer.parseInt(result.output());
        } catch (NumberFormatException e) {
            log.warn("[Tagging] CategoryLevelSkill 返回无效值: {}，使用默认层级 3", result.output());
            return 3;
        }
    }

    private List<Float> generateEmbedding(String text) {
        Random rng = new Random(text.hashCode());
        List<Float> vector = new ArrayList<>(VECTOR_DIM);
        for (int i = 0; i < VECTOR_DIM; i++) {
            vector.add((float) rng.nextGaussian() * 0.1f);
        }
        double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
        if (norm > 0) {
            vector = vector.stream().map(v -> (float) (v / norm)).toList();
        }
        return vector;
    }

    private List<Float> computeContentEmbedding(List<TagInfo> tags) {
        if (tags.isEmpty()) {
            return generateEmbedding(UUID.randomUUID().toString());
        }
        double totalWeight = tags.stream().mapToDouble(TagInfo::confidence).sum();
        List<Float> embedding = new ArrayList<>(VECTOR_DIM);
        for (int i = 0; i < VECTOR_DIM; i++) {
            float sum = 0;
            for (TagInfo tag : tags) {
                sum += tag.vector().get(i) * (float) tag.confidence();
            }
            embedding.add((float) (sum / totalWeight));
        }
        return embedding;
    }
}
