package com.jrl.ai.agent.demo.tagging.controller;

import com.jrl.ai.agent.agentscope.config.AgentResponseHelper;
import com.jrl.ai.agent.demo.tagging.client.MockVectorStorageClient;
import com.jrl.ai.agent.demo.tagging.model.*;
import com.jrl.ai.agent.demo.tagging.mq.CallbackProducer;
import com.jrl.ai.agent.demo.tagging.mq.TaskConsumer;
import com.jrl.ai.agent.demo.tagging.service.TaggingService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 智能打标 REST 端点 — 提供手动触发打标、查询标签、检索相似标签等接口。
 */
@RestController
@RequestMapping("/api/tagging")
public class TaggingController {

    private final TaggingService taggingService;
    private final TaskConsumer taskConsumer;
    private final CallbackProducer callbackProducer;
    private final MockVectorStorageClient vectorClient;

    public TaggingController(TaggingService taggingService,
                              TaskConsumer taskConsumer,
                              CallbackProducer callbackProducer,
                              MockVectorStorageClient vectorClient) {
        this.taggingService = taggingService;
        this.taskConsumer = taskConsumer;
        this.callbackProducer = callbackProducer;
        this.vectorClient = vectorClient;
    }

    /**
     * 直接对内容执行打标（同步）。
     *
     * <pre>
     * POST /api/tagging/tag
     * {
     *   "contentId": "prod_001",
     *   "contentType": "product",
     *   "contentText": "复古胶片风格连衣裙，温暖治愈的秋冬穿搭..."
     * }
     * </pre>
     */
    @PostMapping("/tag")
    public Mono<Map<String, Object>> tag(@RequestBody TagRequest request) {
        return Mono.fromCallable(() -> {
            TaggingResult result = taggingService.tag(
                    request.contentId(),
                    request.contentType(),
                    request.contentText(),
                    request.requiredTagCount() != null ? request.requiredTagCount() : 5
            );

            boolean success = result.error() == null;

            return Map.<String, Object>of(
                    "success", success,
                    "contentId", result.contentId() != null ? result.contentId() : "",
                    "tagCount", result.tags() != null ? result.tags().size() : 0,
                    "tags", result.tags() != null ? result.tags().stream()
                            .map(t -> Map.<String, Object>of(
                                    "id", t.id() != null ? t.id() : "",
                                    "name", t.tagName() != null ? t.tagName() : "",
                                    "category", t.category() != null ? t.category() : "",
                                    "confidence", t.confidence(),
                                    "description", t.description() != null ? t.description() : "",
                                    "keywords", t.keywords() != null ? t.keywords() : List.of()
                            ))
                            .toList() : List.of(),
                    "tokenUsage", AgentResponseHelper.toTokenUsageMap(result.usage()),
                    "trace", AgentResponseHelper.toTraceMap(result.trace()),
                    "processTime", result.processTime(),
                    "evaluation", AgentResponseHelper.toEvaluationMap(result.evaluation()),
                    "optimization", AgentResponseHelper.toOptimizationMap(result.optimization()),
                    "error", result.error() != null ? result.error() : ""
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 通过 MQ 协议提交打标任务（异步）。
     *
     * <pre>
     * POST /api/tagging/submit
     * {
     *   "payloadType": "product",
     *   "payload": {
     *     "contentId": "prod_001",
     *     "title": "复古连衣裙",
     *     "description": "秋冬新款温暖治愈风..."
     *   },
     *   "remark": "优先处理"
     * }
     * </pre>
     */
    @PostMapping("/submit")
    public Mono<Map<String, Object>> submit(@RequestBody SubmitRequest request) {
        return Mono.fromCallable(() -> {
            String taskId = UUID.randomUUID().toString();
            TaggingTask task = TaggingTask.markTag(
                    taskId,
                    request.payloadType(),
                    request.payload(),
                    request.requiredTagCount() != null ? request.requiredTagCount() : 0,
                    request.callbackType(),
                    request.callbackAddress(),
                    request.remark()
            );

            // 模拟 MQ 消费（同步执行，生产环境为异步消费）
            taskConsumer.consume(task);

            // 获取回执
            List<TaggingCallback> callbacks = callbackProducer.getSentCallbacks();
            TaggingCallback callback = callbacks.isEmpty() ? null : callbacks.getLast();

            return Map.<String, Object>of(
                    "success", callback != null && TaggingCallback.STATUS_SUCCESS.equals(callback.status()),
                    "taskId", taskId,
                    "callback", callback != null ? Map.of(
                            "status", callback.status(),
                            "message", callback.message(),
                            "processTime", callback.processTime()
                    ) : Map.of()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询已打标的标签（按 ID 列表）。
     *
     * <pre>
     * POST /api/tagging/query
     * { "ids": ["tag_prod_001_0", "tag_prod_001_1"] }
     * </pre>
     */
    @PostMapping("/query")
    public Mono<Map<String, Object>> query(@RequestBody QueryRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, TagInfo> tags = taggingService.getTagsByIds(request.ids());
            return Map.<String, Object>of(
                    "success", true,
                    "count", tags.size(),
                    "tags", tags.values().stream()
                            .map(t -> Map.<String, Object>of(
                                    "id", t.id(),
                                    "name", t.tagName(),
                                    "category", t.category(),
                                    "confidence", t.confidence(),
                                    "description", t.description() != null ? t.description() : "",
                                    "keywords", t.keywords() != null ? t.keywords() : List.of()
                            ))
                            .toList()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 相似标签检索。
     *
     * <pre>
     * POST /api/tagging/search
     * { "text": "复古胶片", "topK": 10, "minScore": 0.5 }
     * </pre>
     */
    @PostMapping("/search")
    public Mono<Map<String, Object>> search(@RequestBody SearchRequest request) {
        return Mono.fromCallable(() -> {
            // 简化：直接用文本哈希生成查询向量
            List<Float> queryVector = generateQueryVector(request.text());
            List<SearchResult> results = taggingService.searchSimilarTags(
                    queryVector, request.topK(), request.minScore()
            );

            return Map.<String, Object>of(
                    "success", true,
                    "count", results.size(),
                    "results", results.stream()
                            .map(r -> Map.of(
                                    "id", r.id(),
                                    "name", r.tagName(),
                                    "category", r.category(),
                                    "score", r.score()
                            ))
                            .toList()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取系统状态。
     */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> stats() {
        return Mono.fromCallable(() -> Map.<String, Object>of(
                "totalTags", vectorClient.totalTags(),
                "callbacks", callbackProducer.getSentCallbacks().size()
        )).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 基于文本生成查询向量（简化实现，生产环境应调用 Embedding API）。
     *
     * @param text 查询文本
     * @return 归一化后的 768 维向量
     */
    private List<Float> generateQueryVector(String text) {
        Random rng = new Random(text.hashCode());
        List<Float> vector = new ArrayList<>(768);
        for (int i = 0; i < 768; i++) {
            vector.add((float) rng.nextGaussian() * 0.1f);
        }
        double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
        if (norm > 0) {
            vector = vector.stream().map(v -> (float) (v / norm)).toList();
        }
        return vector;
    }

    // ========== Request Records ==========

    /** 同步打标请求体。 */
    public record TagRequest(
            /** 内容 ID */
            String contentId,
            /** 内容类型（product / task / post） */
            String contentType,
            /** 内容文本描述 */
            String contentText,
            /** 要求的标签数量（可选，默认 5） */
            Integer requiredTagCount
    ) {}

    /** MQ 异步交任务请求体。 */
    public record SubmitRequest(
            /** 数据类型（product / task / post） */
            String payloadType,
            /** 具体内容数据 */
            Map<String, Object> payload,
            /** 要求的标签数量（可选，默认 5） */
            Integer requiredTagCount,
            /** 回执方式（可选，mq / http，默认 mq） */
            String callbackType,
            /** 回执地址（MQ 时为 topic，HTTP 时为 URL，默认 tagging_callback） */
            String callbackAddress,
            /** 备注说明 */
            String remark
    ) {}

    /** 标签查询请求体。 */
    public record QueryRequest(
            /** 要查询的标签 ID 列表 */
            List<String> ids
    ) {}

    /** 相似检索请求体。 */
    public record SearchRequest(
            /** 查询文本 */
            String text,
            /** 返回数量上限 */
            int topK,
            /** 最小相似度阈值 */
            double minScore
    ) {}
}
