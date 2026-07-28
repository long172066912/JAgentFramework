package com.jrl.ai.agent.demo.tagging.service;

import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.evaluation.OptimizationReport;
import com.jrl.ai.agent.core.evaluation.OptimizationReportStore;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;
import com.jrl.ai.agent.demo.tagging.client.VectorStorageClient;
import com.jrl.ai.agent.demo.tagging.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能打标服务 — AI Agent 核心流程。
 *
 * <p>流程：内容输入 → LLM 理解并抽取标签 → 生成标签向量 → 存入 Milvus
 *
 * <p>对应架构设计中的「AI Agent 打标」环节：
 * <ol>
 *   <li>内容理解：文本/图片 → 语义表示</li>
 *   <li>标签抽取：匹配标签库 + 生成新标签 + 置信度打分</li>
 *   <li>向量生成：标签描述向量化</li>
 *   <li>数据同步：写入 Milvus</li>
 * </ol>
 */
@Service
public class TaggingService {

    private static final Logger log = LoggerFactory.getLogger(TaggingService.class);

    /** 标签向量维度（与 Milvus collection 配置一致） */
    private static final int VECTOR_DIM = 768;

    /** 标签集合名称 */
    private static final String TAG_COLLECTION = "tag_vectors";

    /** 默认标签数量（未指定时使用） */
    private static final int DEFAULT_TAG_COUNT = 5;

    /** 标签数量不足时的最大重试次数 */
    private static final int MAX_TAG_RETRIES = 3;

    /** 从 LLM 输出中解析标签的正则（包含 description 和 keywords） */
    private static final Pattern TAG_PATTERN =
            Pattern.compile("\\[TAG\\]\\s*name=(.+?),\\s*category=(.+?),\\s*confidence=([\\d.]+),\\s*desc=(.+?),\\s*keywords=(.+)");

    private final AgentFactory agentFactory;
    private final VectorStorageClient vectorClient;
    private final EvaluationStore evaluationStore;
    private final OptimizationReportStore optimizationReportStore;

    public TaggingService(AgentFactory agentFactory, VectorStorageClient vectorClient,
                          @Autowired(required = false) EvaluationStore evaluationStore,
                          @Autowired(required = false) OptimizationReportStore optimizationReportStore) {
        this.agentFactory = agentFactory;
        this.vectorClient = vectorClient;
        this.evaluationStore = evaluationStore;
        this.optimizationReportStore = optimizationReportStore;
        log.info("[TaggingService] Initialized: evaluationStore={}, optimizationReportStore={}",
                evaluationStore != null, optimizationReportStore != null);
    }

    /**
     * 对内容执行智能打标（使用默认标签数量 5）。
     *
     * @param contentId   内容 ID
     * @param contentType 内容类型（product / task / post）
     * @param contentText 内容文本描述
     * @return 打标结果
     */
    public TaggingResult tag(String contentId, String contentType, String contentText) {
        return tag(contentId, contentType, contentText, DEFAULT_TAG_COUNT);
    }

    /**
     * 对内容执行智能打标。
     *
     * @param contentId        内容 ID
     * @param contentType      内容类型（product / task / post）
     * @param contentText      内容文本描述
     * @param requiredTagCount 要求的标签数量
     * @return 打标结果
     */
    public TaggingResult tag(String contentId, String contentType, String contentText, int requiredTagCount) {
        ExecutionTrace.Builder traceBuilder = ExecutionTrace.builder().start();
        long start = System.currentTimeMillis();
        log.info("[Tagging] start contentId={} type={}", contentId, contentType);
    
        try {
            // 1. 调用 LLM 进行内容理解 + 标签抽取
            long stepStart = System.currentTimeMillis();
            LLMCallResult llmResult = callLLMForTagging(contentText, contentType, requiredTagCount);
            long llmDuration = System.currentTimeMillis() - stepStart;
    
            // 合并 Agent 层 trace（AgentScope 适配器自动填充的）
            if (llmResult.taskResult().trace() != null) {
                for (ExecutionTrace.Step s : llmResult.taskResult().trace().steps()) {
                    traceBuilder.step(s.name(), s.duration(), s.detail());
                }
            } else {
                traceBuilder.step("LLM_CALL", llmDuration,
                        "model=%s, tags=%d".formatted(
                                llmResult.tokenUsage() != null ? llmResult.tokenUsage().modelId() : "unknown",
                                countTags(llmResult.output())));
            }
            log.debug("[Tagging] LLM output: {}", llmResult.output());
    
            // 2. 解析 LLM 输出，提取标签
            stepStart = System.currentTimeMillis();
            List<TagInfo> tags = parseTags(llmResult.output(), contentId);
            traceBuilder.step("PARSE_TAGS", System.currentTimeMillis() - stepStart,
                    "parsed=%d tags".formatted(tags.size()));
    
            // 3. 为每个标签生成向量（简化：基于标签名生成伪向量，生产环境调用 Embedding API）
            stepStart = System.currentTimeMillis();
            for (int i = 0; i < tags.size(); i++) {
                TagInfo tag = tags.get(i);
                List<Float> vector = generateEmbedding(tag.tagName());
                TagInfo withVector = new TagInfo(
                        tag.id(), tag.tagName(), tag.category(), tag.level(),
                        tag.status(), vector, tag.confidence(), tag.description(),
                        tag.keywords(), tag.extraFields()
                );
                tags.set(i, withVector);
            }
            traceBuilder.step("EMBEDDING", System.currentTimeMillis() - stepStart,
                    "generated %d vectors (dim=%d)".formatted(tags.size(), VECTOR_DIM));
    
            // 4. 写入 Milvus
            stepStart = System.currentTimeMillis();
            int upserted = vectorClient.batchUpsert(TAG_COLLECTION, tags);
            traceBuilder.step("MILVUS_UPSERT", System.currentTimeMillis() - stepStart,
                    "upserted=%d to %s".formatted(upserted, TAG_COLLECTION));
            log.info("[Tagging] upserted {} tags for contentId={}", upserted, contentId);
    
            // 5. 计算内容整体向量（标签向量加权平均）
            stepStart = System.currentTimeMillis();
            List<Float> contentEmbedding = computeContentEmbedding(tags);
            traceBuilder.step("CONTENT_EMBEDDING", System.currentTimeMillis() - stepStart,
                    "weighted avg of %d tag vectors".formatted(tags.size()));
    
            long processTime = System.currentTimeMillis() - start;
            log.info("[Tagging] done contentId={}, tags={}, time={}ms", contentId, tags.size(), processTime);

            // 查询本次打标的评测结果（评测系统启用时自动触发）
            EvaluationResult evaluation = null;
            OptimizationReport optimization = null;
            if (evaluationStore != null) {
                String actualAgentId = llmResult.agentId();
                List<EvaluationResult> results = evaluationStore.findByAgent(actualAgentId, 1);
                log.info("[Tagging] evaluation query: agentId={}, found={} results", actualAgentId, results.size());
                if (!results.isEmpty()) {
                    evaluation = results.getFirst();
                    log.info("[Tagging] evaluation: evalId={} composite={}", evaluation.evalId(), evaluation.compositeScore());
                    
                    // 查询优化建议报告
                    if (optimizationReportStore != null) {
                        log.info("[Tagging] querying optimization reports for agentId={}", actualAgentId);
                        List<OptimizationReport> reports = optimizationReportStore.findByAgent(actualAgentId, 1);
                        log.info("[Tagging] optimization reports found: {}", reports.size());
                        if (!reports.isEmpty()) {
                            optimization = reports.getFirst();
                            log.info("[Tagging] optimization: suggestions={}", optimization.suggestions().size());
                        }
                    } else {
                        log.warn("[Tagging] optimizationReportStore is null");
                    }
                } else {
                    log.warn("[Tagging] No evaluation result found for agentId={}. " +
                            "Check if EvaluationInterceptor is registered and jagent.evaluation.enabled=true", actualAgentId);
                }
            } else {
                log.warn("[Tagging] EvaluationStore is null - evaluation system not enabled");
            }

            ExecutionTrace trace = traceBuilder.build();
            return new TaggingResult(contentId, contentType, tags, contentEmbedding,
                    llmResult.tokenUsage(), trace, processTime, evaluation, optimization, null);
    
        } catch (Exception e) {
            log.error("[Tagging] failed contentId={}", contentId, e);
            long processTime = System.currentTimeMillis() - start;
            ExecutionTrace trace = traceBuilder.build();

            // 尝试获取评测结果（即使打标失败，Agent 执行时可能已触发评测）
            EvaluationResult evaluation = null;
            OptimizationReport optimization = null;
            if (evaluationStore != null) {
                List<EvaluationResult> results = evaluationStore.findByAgent("tagger", 1);
                if (!results.isEmpty()) {
                    evaluation = results.getFirst();
                    // 查询优化建议报告
                    if (optimizationReportStore != null) {
                        List<OptimizationReport> reports = optimizationReportStore.findByAgent("tagger", 1);
                        if (!reports.isEmpty()) {
                            optimization = reports.getFirst();
                        }
                    }
                }
            }

            // 返回部分结果（包含链路和评测信息）
            return new TaggingResult(contentId, contentType, List.of(), List.of(),
                    null, trace, processTime, evaluation, optimization, "打标失败: " + e.getMessage());
        }
    }

    /**
     * 统计 LLM 输出中的标签数量。
     */
    private int countTags(String output) {
        return (int) TAG_PATTERN.matcher(output).results().count();
    }

    /**
     * LLM 调用结果（包含输出、Token 消耗、完整 TaskResult 和 Agent ID）。
     */
    private record LLMCallResult(String output, TokenUsage tokenUsage, TaskResult taskResult, String agentId) {}

    /**
     * 调用 LLM 进行内容理解和标签抽取，确保输出恰好 requiredTagCount 个标签。
     *
     * <p>如果 LLM 输出的标签数量不足，会自动重试（最多 MAX_TAG_RETRIES 次），
     * 并在重试时将数量要求追加到提示词中。
     *
     * @param contentText      内容文本
     * @param contentType      内容类型
     * @param requiredTagCount 要求的标签数量
     * @return LLM 调用结果（包含输出、Token 消耗和完整 TaskResult）
     * @throws TaggingException LLM 调用失败或多次重试后仍不满足数量要求时抛出
     */
    private LLMCallResult callLLMForTagging(String contentText, String contentType, int requiredTagCount) {
        Agent agent = agentFactory.getAgent("tagger");
        String basePrompt = buildTaggingPrompt(contentText, contentType, requiredTagCount);

        for (int attempt = 1; attempt <= MAX_TAG_RETRIES; attempt++) {
            // 首次使用基础提示词，重试时追加数量修正提示
            String prompt = attempt == 1 ? basePrompt : appendRetryHint(basePrompt, requiredTagCount);

            ChatMessage input = ChatMessage.user(prompt);
            AgentContext context = AgentContext.builder()
                    .sessionId(UUID.randomUUID().toString())
                    .userId("tagging-system")
                    .build();

            TaskResult result = agent.execute(input, context);
            if (!result.isSuccess()) {
                String errorMsg = result.error() != null ? result.error().getMessage() : "未知错误";
                throw new TaggingException("LLM 调用失败: " + errorMsg, result.error());
            }

            String output = (String) result.result().getOrDefault("response", "");
            int tagCount = countTags(output);

            if (tagCount == requiredTagCount) {
                log.info("[Tagging] LLM 输出 {} 个标签，符合要求（第 {} 次尝试）", tagCount, attempt);
                return new LLMCallResult(output, result.usage(), result, agent.id());
            }

            log.warn("[Tagging] LLM 输出 {} 个标签，要求 {} 个，第 {}/{} 次尝试",
                    tagCount, requiredTagCount, attempt, MAX_TAG_RETRIES);
        }

        throw new TaggingException(
                "LLM 多次尝试后仍无法输出恰好 %d 个标签（已重试 %d 次）".formatted(requiredTagCount, MAX_TAG_RETRIES),
                null);
    }

    /**
     * 追加数量修正提示到基础提示词末尾。
     *
     * @param basePrompt       基础提示词
     * @param requiredTagCount 要求的标签数量
     * @return 带修正提示的完整提示词
     */
    private String appendRetryHint(String basePrompt, int requiredTagCount) {
        return basePrompt + "\n\n【重要修正】上一次输出的标签数量不正确。" +
                "请务必输出恰好 %d 个标签，不多不少。每个标签独占一行，以 [TAG] 开头。".formatted(requiredTagCount);
    }

    /**
     * 构建打标提示词。
     *
     * @param contentText      内容文本
     * @param contentType      内容类型
     * @param requiredTagCount 要求的标签数量
     * @return 完整的打标提示词
     */
    private String buildTaggingPrompt(String contentText, String contentType, int requiredTagCount) {
        return """
                请分析以下%s内容，抽取语义标签。
                
                内容：
                %s
                
                要求：
                1. 必须抽取恰好 %d 个最能代表内容语义的标签（不多不少）
                2. 每个标签包含：
                   - name: 标签名称
                   - category: 类目（视觉风格/情感氛围/场景用途/材质工艺/颜色配色/主题元素）
                   - confidence: 置信度（0-1）
                   - desc: 标签介绍（一句话描述该标签的语义含义）
                   - keywords: 关键词（3-5个，用/分隔，用于搜索匹配）
                3. 严格按以下格式输出，每行一个标签：
                [TAG] name=标签名称, category=类目, confidence=置信度, desc=标签介绍, keywords=关键词1/关键词2/关键词3
                
                示例：
                [TAG] name=复古胶片风, category=视觉风格, confidence=0.95, desc=模拟传统胶片相机的色彩质感和颗粒感, keywords=胶片/复古/颗粒感/怀旧/相机
                [TAG] name=温暖治愈, category=情感氛围, confidence=0.88, desc=给人温暖舒适、心灵治愈的感觉, keywords=温暖/治愈/舒适/温馨
                """.formatted(contentType, contentText, requiredTagCount);
    }

    /**
     * 解析 LLM 输出中的标签。
     *
     * @param llmOutput LLM 原始输出文本
     * @param contentId 内容 ID（用于生成标签 ID 前缀）
     * @return 解析出的标签列表（可能为空）
     */
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

        if (tags.isEmpty()) {
            log.warn("[Tagging] No tags parsed from LLM output, contentId={}", contentId);
        }

        return tags;
    }

    /**
     * 根据类目推断标签层级。
     *
     * @param category 标签类目
     * @return 标签层级（1/2/3）
     */
    private int inferLevel(String category) {
        return switch (category) {
            case "视觉风格", "情感氛围" -> 1; // 一级标签
            case "场景用途", "材质工艺" -> 2; // 二级标签
            default -> 3;                     // 三级标签
        };
    }

    /**
     * 生成标签的 Embedding 向量。
     *
     * <p>简化实现：基于标签名哈希生成伪向量，保证相同标签生成相同向量。
     * 生产环境替换为调用 DashScope Embedding API（text-embedding-v4）。
     */
    private List<Float> generateEmbedding(String text) {
        Random rng = new Random(text.hashCode());
        List<Float> vector = new ArrayList<>(VECTOR_DIM);
        for (int i = 0; i < VECTOR_DIM; i++) {
            vector.add((float) rng.nextGaussian() * 0.1f);
        }
        // 归一化
        double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
        if (norm > 0) {
            vector = vector.stream().map(v -> (float) (v / norm)).toList();
        }
        return vector;
    }

    /**
     * 计算内容整体向量（标签向量加权平均，权重为置信度）。
     */
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

    /**
     * 查询已存在的标签。
     *
     * @param ids 标签 ID 列表
     * @return ID → 标签信息映射
     */
    public Map<String, TagInfo> getTagsByIds(List<String> ids) {
        return vectorClient.batchGet(TAG_COLLECTION, ids);
    }

    /**
     * 相似标签检索。
     *
     * @param vector   查询向量
     * @param topK     返回数量上限
     * @param minScore 最小相似度阈值
     * @return 检索结果列表，按相似度降序
     */
    public List<SearchResult> searchSimilarTags(List<Float> vector, int topK, double minScore) {
        return vectorClient.searchSimilar(TAG_COLLECTION, vector, topK, "status == 1", minScore);
    }

    /**
     * 打标异常 — 打标流程中任意环节失败时抛出。
     */
    public static class TaggingException extends RuntimeException {
        public TaggingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
