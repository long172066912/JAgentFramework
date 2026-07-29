package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.core.task.AgentResponse;
import com.jrl.ai.agent.demo.service.AgentService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;

/**
 * Agent REST 端点 — 提供同步对话和 Agent 列表查询。
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
     * <p>直接返回 AgentResponse<String>，框架自动序列化公共字段。
     */
    @PostMapping("/chat")
    public Mono<AgentResponse<String>> chat(@RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> {
            String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
            String userId = request.userId() != null ? request.userId() : "anonymous";
            return agentService.chat(request.agentKey(), request.text(), sessionId, userId);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 列出所有已注册的 Agent。
     *
     * @return Agent 标识 → 名称映射
     */
    @GetMapping("/list")
    public Mono<Map<String, String>> list() {
        return Mono.fromCallable(agentService::listAgents)
                .subscribeOn(Schedulers.boundedElastic());
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
