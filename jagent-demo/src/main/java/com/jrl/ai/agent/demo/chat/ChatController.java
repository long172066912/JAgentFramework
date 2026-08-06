package com.jrl.ai.agent.demo.chat;

import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.demo.service.AgentService;
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
 * 智能对话控制器 — 类 Kimi 的多轮对话。
 *
 * <p>评测由 AgentService 链路内置处理，Controller 只负责 HTTP 协议适配。
 * <p>流式端点内部通过同步 execute() 实现，客户端通过 sessionId 关联上下文。
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
     * SSE 流式对话 — 利用 Agent 原生流式能力。
     *
     * <p>通过虚拟线程异步调度，实现真正的文本增量推送。
     * 客户端通过 sessionId 关联上下文，多次调用自动补齐历史。
     *
     * @param request 对话请求
     * @return SSE 事件流（文本增量）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody StreamRequest request) {
        String sid = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        String uid = request.userId() != null ? request.userId() : "chat-user";
        String agentKey = request.agentKey() != null ? request.agentKey() : "chat";
        return agentService.stream(agentKey, request.text(), sid, uid);
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
     * 查询会话最新评测结果 — 返回置信度评分与链路快照。
     *
     * <p>链路快照（span 树 + 多维分析）随评测记录一同返回，
     * 框架不持久化 span，无需二次查询。
     *
     * @param sessionId 会话 ID
     * @return 评分信息 + 链路快照
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
            latest.scores().forEach((dim, ds) -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("score", ds.score());
                d.put("reason", ds.reason());
                d.put("metrics", ds.metrics());
                dims.put(dim.name(), d);
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", score);
            result.put("label", label);
            result.put("dimensions", dims);
            result.put("traceId", latest.traceId());
            result.put("evalId", latest.evalId());
            // 链路快照归属专门的 trace（不放在评测字段内）
            Map<String, Object> trace = new LinkedHashMap<>();
            if (latest.trace() != null) {
                trace.put("steps", latest.trace().steps());
                trace.put("totalTime", latest.trace().totalTime());
                if (latest.trace().otel() != null) {
                    trace.put("traceId", latest.trace().otel().traceId());
                    trace.put("analysis", latest.trace().otel().analysis());
                    trace.put("spans", latest.trace().otel().spans());
                }
            }
            result.put("trace", trace);
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
