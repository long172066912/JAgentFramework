package com.jrl.ai.agent.demo.chat;

import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.agentscope.evaluation.RuleBasedEvaluator;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.demo.service.AgentService;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能对话控制器 — 类 Kimi 的多轮对话，支持 SSE 流式输出。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentService agentService;
    private final AgentFactory agentFactory;
    private final EvaluationStore evaluationStore;
    private final RuleBasedEvaluator ruleBasedEvaluator;
    private final CompositeScorer compositeScorer;

    public ChatController(AgentService agentService, AgentFactory agentFactory,
                          EvaluationStore evaluationStore, RuleBasedEvaluator ruleBasedEvaluator,
                          CompositeScorer compositeScorer) {
        this.agentService = agentService;
        this.agentFactory = agentFactory;
        this.evaluationStore = evaluationStore;
        this.ruleBasedEvaluator = ruleBasedEvaluator;
        this.compositeScorer = compositeScorer;
    }

    /**
     * SSE 流式对话 — 实时推送文本增量。
     *
     * <p>前端通过 EventSource 连接，实时接收 Agent 输出的文本片段。
     * sessionId 由前端维护，同一会话复用同一 sessionId 实现多轮对话。
     *
     * @param request 对话请求（agentKey + text + sessionId）
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody StreamRequest request) {
        String sid = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        String uid = request.userId() != null ? request.userId() : "chat-user";
        String agentKey = request.agentKey() != null ? request.agentKey() : "chat";
        String userInput = request.text();

        // 收集流式输出文本，流结束后执行评测
        StringBuilder outputCollector = new StringBuilder();

        return agentService.stream(agentKey, userInput, sid, uid)
                .filter(event -> event.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .map(event -> {
                    if (event instanceof TextBlockDeltaEvent delta) {
                        return delta.getDelta();
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .doOnNext(outputCollector::append)
                .doFinally(signal -> {
                    // 流结束后异步执行评测并保存结果
                    String output = outputCollector.toString();
                    if (!output.isEmpty()) {
                        runEvaluation(agentKey, sid, userInput, output);
                    }
                });
    }

    /**
     * 流结束后执行规则评测并保存结果。
     */
    private void runEvaluation(String agentKey, String sessionId, String input, String output) {
        try {
            var agent = agentFactory.getAgent(agentKey);
            EvaluationContext context = EvaluationContext.of(agent.id(), input, output, null);

            EvaluationResult evalResult = ruleBasedEvaluator.evaluate(context);
            double compositeScore = compositeScorer.compute(evalResult.scores());

            EvaluationResult finalResult = EvaluationResult.builder(agent.id())
                    .sessionId(sessionId)
                    .scores(evalResult.scores())
                    .compositeScore(compositeScore)
                    .input(input)
                    .output(output)
                    .build();

            evaluationStore.save(finalResult);
            log.info("[Chat] 评测完成: agent={} session={} score={}",
                    agentKey, sessionId, String.format("%.2f", compositeScore));
        } catch (Exception e) {
            log.warn("[Chat] 评测失败: {}", e.getMessage());
        }
    }

    /**
     * 创建新会话 — 返回 sessionId。
     */
    @PostMapping("/session")
    public Mono<Map<String, String>> newSession() {
        return Mono.fromCallable(() -> Map.of("sessionId", UUID.randomUUID().toString()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取 Agent 信息。
     */
    @GetMapping("/info")
    public Mono<Map<String, String>> info(@RequestParam(defaultValue = "chat") String agentKey) {
        return Mono.fromCallable(() -> {
            var agent = agentFactory.getAgent(agentKey);
            return Map.of("agentId", agent.id(), "agentName", agent.name());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询会话最新评测结果 — 返回置信度评分。
     *
     * @param sessionId 会话 ID
     * @return 置信度评分信息
     */
    @GetMapping("/evaluation")
    public Mono<Map<String, Object>> evaluation(@RequestParam String sessionId) {
        return Mono.fromCallable(() -> {
            List<EvaluationResult> results = evaluationStore.findBySession(sessionId);
            if (results.isEmpty()) {
                return Map.<String, Object>of("score", -1, "label", "评测中...");
            }
            EvaluationResult latest = results.get(0);
            double score = latest.compositeScore();
            String label = score >= 0.9 ? "优秀" : score >= 0.7 ? "良好" : score >= 0.5 ? "一般" : "较差";

            Map<String, Object> dims = new LinkedHashMap<>();
            latest.scores().forEach((dim, ds) -> dims.put(dim.name(), ds.score()));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", score);
            result.put("label", label);
            result.put("dimensions", dims);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 流式对话请求体。
     */
    public record StreamRequest(
            /** Agent 标识（可选，默认 "chat"） */
            String agentKey,
            /** 用户输入 */
            String text,
            /** 会话 ID（可选，不传则新建） */
            String sessionId,
            /** 用户 ID（可选） */
            String userId
    ) {}
}
