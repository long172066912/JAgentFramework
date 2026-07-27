package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.demo.service.AgentService;
import com.jrl.ai.agent.core.task.TaskResult;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Agent REST 端点 — 提供同步对话、SSE 流式输出和 Agent 列表查询。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 同步对话端点 — 一次性返回完整结果。
     *
     * @param request 对话请求（agentKey + text）
     * @return 对话结果
     */
    @PostMapping("/chat")
    public Mono<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> {
            String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
            String userId = request.userId() != null ? request.userId() : "anonymous";

            TaskResult result = agentService.chat(request.agentKey(), request.text(), sessionId, userId);

            return Map.<String, Object>of(
                    "success", result.isSuccess(),
                    "response", result.result().getOrDefault("response", ""),
                    "model", result.result().getOrDefault("model", ""),
                    "sessionId", sessionId,
                    "durationMs", result.durationMs()
            );
        });
    }

    /**
     * SSE 流式对话端点 — 实时推送文本增量。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入
     * @param sessionId 会话 ID（可选）
     * @param userId    用户 ID（可选）
     * @return SSE 事件流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam String agentKey,
            @RequestParam String text,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId) {

        String sid = sessionId != null ? sessionId : UUID.randomUUID().toString();
        String uid = userId != null ? userId : "anonymous";

        return agentService.stream(agentKey, text, sid, uid)
                .filter(event -> event.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .map(event -> {
                    if (event instanceof TextBlockDeltaEvent delta) {
                        return delta.getDelta();
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty());
    }

    /**
     * 列出所有已注册的 Agent。
     *
     * @return Agent 标识 → 名称映射
     */
    @GetMapping("/list")
    public Mono<Map<String, String>> list() {
        return Mono.fromCallable(agentService::listAgents);
    }

    /**
     * 对话请求体。
     */
    public record ChatRequest(
            /** Agent 标识（对应 application.yml 中的 key） */
            String agentKey,
            /** 用户输入文本 */
            String text,
            /** 会话 ID（可选，不传则自动生成） */
            String sessionId,
            /** 用户 ID（可选） */
            String userId
    ) {}
}
