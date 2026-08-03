package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.agentscope.async.AsyncTaskManager;
import com.jrl.ai.agent.core.task.AgentResponse;
import com.jrl.ai.agent.demo.service.AgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Agent REST 端点 — 提供同步对话、SSE 流式输出和 Agent 列表查询。
 *
 * <p>流式端点内部通过同步 execute() 实现，客户端通过 sessionId 关联上下文。
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
     * SSE 流式对话端点 — 利用 Agent 原生流式能力。
     *
     * <p>通过虚拟线程异步调度，实现真正的文本增量推送。
     * 客户端通过 sessionId 关联上下文，多次调用自动补齐历史。
     *
     * @param request 对话请求
     * @return SSE 事件流（文本增量）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        String userId = request.userId() != null ? request.userId() : "anonymous";
        return agentService.stream(request.agentKey(), request.text(), sessionId, userId);
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

    // ==================== 异步任务（短连接） ====================

    /**
     * 异步执行任务 — 立即返回 taskId，客户端可断开。
     *
     * <p>任务在后台异步执行，客户端可通过 taskId 查询状态或订阅事件流。
     * 当 Agent 遇到需要确认的工具时，任务暂停，通过事件流通知客户端。
     */
    @PostMapping("/execute")
    public Mono<Map<String, String>> executeAsync(@RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> {
            String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
            String userId = request.userId() != null ? request.userId() : "anonymous";
            String taskId = agentService.executeAsync(request.agentKey(), request.text(), sessionId, userId);
            return Map.of("taskId", taskId, "sessionId", sessionId);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 确认异步任务 — 用户确认后恢复执行。
     *
     * @param request 确认请求（taskId + confirmed）
     * @return 任务信息
     */
    @PostMapping("/confirm")
    public Mono<AsyncTaskManager.TaskInfo> confirm(@RequestBody ConfirmRequest request) {
        return Mono.fromCallable(() ->
                agentService.confirmTask(request.taskId(), request.confirmed())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询异步任务状态。
     *
     * @param taskId 任务标识
     * @return 任务信息
     */
    @GetMapping("/task/{taskId}")
    public Mono<AsyncTaskManager.TaskInfo> getTask(@PathVariable String taskId) {
        return Mono.fromCallable(() ->
                agentService.getTaskInfo(taskId)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * SSE 订阅异步任务事件流。
     *
     * <p>实时推送任务状态变化：text_delta / need_confirm / completed / failed。
     *
     * @param taskId 任务标识
     * @return SSE 事件流
     */
    @GetMapping(value = "/task/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AsyncTaskManager.TaskEvent> taskEvents(@PathVariable String taskId) {
        return agentService.subscribeTask(taskId);
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

    /**
     * 确认请求体。
     */
    public record ConfirmRequest(
            /** 任务标识 */
            String taskId,
            /** 是否确认 */
            boolean confirmed
    ) {}
}
