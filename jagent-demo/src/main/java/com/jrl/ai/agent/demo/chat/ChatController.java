package com.jrl.ai.agent.demo.chat;

import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.demo.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能对话控制器 — 类 Kimi 的多轮对话。
 *
 * <p>评测由 AgentService 链路内置处理，Controller 只负责 HTTP 协议适配。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentService agentService;
    private final AgentFactory agentFactory;
    private final EvaluationStore evaluationStore;

    public ChatController(AgentService agentService, AgentFactory agentFactory,
                          EvaluationStore evaluationStore) {
        this.agentService = agentService;
        this.agentFactory = agentFactory;
        this.evaluationStore = evaluationStore;
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
}
