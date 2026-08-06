/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.async;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import org.checkerframework.checker.index.qual.NonNegative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 异步任务管理器 — 纯异步 Stream 化架构，支持短连接 + 用户确认机制。
 *
 * <p>核心设计：
 * <ul>
 *   <li>纯异步：全程 Flux.subscribe()，无任何阻塞调用</li>
 *   <li>Stream 化：内部通过 Sinks.Many 桥接事件流，支持多订阅者</li>
 *   <li>短连接友好：execute() 立即返回 taskId，客户端可断开</li>
 *   <li>事件驱动状态机：每个 AgentEvent 触发状态迁移</li>
 *   <li>确认恢复：confirmAndResume() 带 ConfirmResult 重新调用 adapter 的 streamAgentEvents</li>
 *   <li>执行监听：由 adapter 内部封装，本类不感知监听器</li>
 * </ul>
 *
 * <p>短连接流程：
 * <pre>
 * 客户端                          服务端
 *   |--- POST /execute ----------->|  返回 taskId，客户端可断开
 *   |                              |  纯异步订阅事件流
 *   |                              |  检测到 RequireUserConfirmEvent → 暂停
 *   |                              |  通过 Sink 发射 need_confirm 事件
 *   |                              |
 *   |--- POST /confirm ----------->|  用户确认，恢复执行
 *   |                              |  带 ConfirmResult 重新调用 streamEvents
 *   |                              |  通过 Sink 发射 completed 事件
 *   |--- GET  /task/{taskId} ----->|  查询任务状态/结果
 *   |--- GET  /task/{taskId}/events >|  SSE 订阅任务事件流
 * </pre>
 */
public class AsyncTaskManager {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskManager.class);

    /** 已完成任务保留时长（分钟），过期后自动从缓存移除 */
    private static final long COMPLETED_RETENTION_MINUTES = 5;

    /** 等待确认超时时长（分钟），超时后自动标记为 FAILED */
    private static final long CONFIRM_TIMEOUT_MINUTES = 10;

    /** 任务状态 */
    public enum TaskStatus {
        RUNNING, WAITING_CONFIRM, RESUMING, COMPLETED, FAILED
    }

    /** 任务信息（不可变快照） */
    public record TaskInfo(
            String taskId,
            TaskStatus status,
            List<PendingToolCall> pendingToolCalls,
            String result,
            String error
    ) {}

    /** 待确认的工具调用 */
    public record PendingToolCall(
            String toolCallId,
            String toolName,
            Map<String, Object> input
    ) {}

    /** 任务事件（发射给订阅者） */
    public record TaskEvent(
            String taskId,
            String eventType,
            Object data
    ) {}

    // 事件类型常量
    public static final String EVENT_TEXT_DELTA = "text_delta";
    public static final String EVENT_NEED_CONFIRM = "need_confirm";
    public static final String EVENT_COMPLETED = "completed";
    public static final String EVENT_FAILED = "failed";

    /**
     * Caffeine 缓存替代 ConcurrentHashMap，按任务状态动态设置 TTL：
     * <ul>
     *   <li>RUNNING / RESUMING — 不过期（任务进行中）</li>
     *   <li>WAITING_CONFIRM — {@value CONFIRM_TIMEOUT_MINUTES} 分钟后过期，超时自动 FAILED</li>
     *   <li>COMPLETED / FAILED — {@value COMPLETED_RETENTION_MINUTES} 分钟后过期（保留查询窗口）</li>
     * </ul>
     */
    private final Cache<String, TaskContext> tasks = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(new TaskExpiry())
            .removalListener((key, ctx, cause) -> {
                if (ctx instanceof TaskContext tc) {
                    // 等待确认超时 → 自动标记 FAILED
                    if (tc.status == TaskStatus.WAITING_CONFIRM) {
                        tc.status = TaskStatus.FAILED;
                        tc.error = "Confirmation timeout after " + CONFIRM_TIMEOUT_MINUTES + " minutes";
                        log.warn("[AsyncTask] Task {} expired while WAITING_CONFIRM, marked FAILED", tc.taskId);
                        tc.eventSink.tryEmitNext(new TaskEvent(
                                tc.taskId, EVENT_FAILED,
                                Map.of("taskId", tc.taskId, "error", tc.error)));
                    }
                    // 释放重量级引用，帮助 GC
                    tc.release();
                    tc.eventSink.tryEmitComplete();
                }
            })
            .build();

    /**
     * 异步执行 Agent 任务（纯 Stream 化，立即返回 taskId）。
     *
     * @param agent   Agent 适配器
     * @param input   用户输入
     * @param context 运行上下文
     * @return taskId 任务标识
     */
    public String execute(AgentScopeAgentAdapter agent, ChatMessage input,
                          AgentContext context) {
        String taskId = UUID.randomUUID().toString();
        TaskContext ctx = new TaskContext(taskId, agent, context);
        tasks.put(taskId, ctx);

        // 纯异步订阅事件流，无阻塞（执行监听器通知由 adapter 内部封装）
        agent.streamAgentEvents(input, context)
                .subscribe(
                        event -> onAgentEvent(ctx, event),
                        error -> onAgentError(ctx, error),
                        () -> onAgentComplete(ctx)
                );

        log.info("[AsyncTask] Task {} submitted", taskId);
        return taskId;
    }

    /**
     * 确认并恢复任务执行（纯异步）。
     *
     * @param taskId    任务标识
     * @param confirmed 是否确认
     * @return 任务信息快照
     */
    public TaskInfo confirmAndResume(String taskId, boolean confirmed) {
        TaskContext ctx = tasks.getIfPresent(taskId);
        if (ctx == null) {
            throw new IllegalArgumentException("Task not found or expired: " + taskId);
        }
        if (ctx.status != TaskStatus.WAITING_CONFIRM) {
            throw new IllegalStateException(
                    "Task " + taskId + " is not waiting for confirmation (status=" + ctx.status + ")");
        }

        ctx.confirmed = confirmed;
        ctx.status = TaskStatus.RESUMING;
        refreshExpiry(ctx); // RESUMING → 不过期
        log.info("[AsyncTask] Task {} confirmed={}, resuming...", taskId, confirmed);

        // 构建带 ConfirmResult 的消息
        List<ConfirmResult> confirmResults = new ArrayList<>();
        if (ctx.pendingToolCalls != null) {
            for (PendingToolCall ptc : ctx.pendingToolCalls) {
                ToolUseBlock toolCall = new ToolUseBlock(
                        ptc.toolCallId(), ptc.toolName(), ptc.input());
                confirmResults.add(new ConfirmResult(confirmed, toolCall));
            }
        }

        Msg confirmMsg = UserMessage.builder()
                .textContent("[User confirmed the pending tool calls]")
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                .build();

        AgentScopeAgentAdapter agent = ctx.agent;

        // 纯异步恢复执行（执行监听器通知由 adapter 内部封装）
        agent.streamAgentEvents(ChatMessage.user(confirmMsg.getTextContent()), confirmMsg, ctx.context)
                .subscribe(
                        event -> onAgentEvent(ctx, event),
                        error -> onAgentError(ctx, error),
                        () -> onAgentComplete(ctx)
                );

        return getTaskInfo(taskId);
    }

    /**
     * 订阅任务事件流（用于 SSE 推送或回调桥接）。
     *
     * <p>返回一个 Flux，订阅后可以实时接收任务事件。
     * 框架层不依赖 SSE，由调用方决定如何消费。
     *
     * @param taskId 任务标识
     * @return 任务事件流
     */
    public Flux<TaskEvent> subscribe(String taskId) {
        TaskContext ctx = tasks.getIfPresent(taskId);
        if (ctx == null) {
            return Flux.error(new IllegalArgumentException("Task not found or expired: " + taskId));
        }
        return ctx.eventSink.asFlux();
    }

    /**
     * 注册一次性回调（简化版，等价于 subscribe + filter）。
     *
     * @param taskId   任务标识
     * @param callback 回调函数 (eventType, data)
     */
    public void onEvent(String taskId, BiConsumer<String, Object> callback) {
        subscribe(taskId).subscribe(
                event -> callback.accept(event.eventType(), event.data()),
                error -> callback.accept(EVENT_FAILED, error.getMessage())
        );
    }

    /**
     * 获取任务信息快照。
     */
    public TaskInfo getTaskInfo(String taskId) {
        TaskContext ctx = tasks.getIfPresent(taskId);
        if (ctx == null) {
            return null;
        }
        return new TaskInfo(
                taskId, ctx.status,
                ctx.pendingToolCalls, ctx.result.toString(), ctx.error
        );
    }

    /**
     * 手动清理任务（Caffeine 会自动过期，通常无需手动调用）。
     */
    public void cleanup(String taskId) {
        tasks.invalidate(taskId);
    }

    /**
     * 列出所有任务。
     */
    public Collection<TaskInfo> listTasks() {
        tasks.cleanUp(); // 触发过期检查
        return tasks.asMap().values().stream()
                .map(c -> new TaskInfo(c.taskId, c.status,
                        c.pendingToolCalls, c.result.toString(), c.error))
                .toList();
    }

    // ==================== 事件驱动状态机 ====================

    /**
     * 状态迁移后刷新 Caffeine 缓存的过期时间。
     *
     * <p>因为 TaskContext 是原地修改（不是 replace），Caffeine 的 Expiry 回调不会自动触发，
     * 必须通过 {@link com.github.benmanes.caffeine.cache.VarExpiration#setExpiresAfter} 主动刷新。
     */
    private void refreshExpiry(TaskContext ctx) {
        tasks.policy().expireVariably().ifPresent(varExp -> {
            long nanos = switch (ctx.status) {
                case WAITING_CONFIRM -> TimeUnit.MINUTES.toNanos(CONFIRM_TIMEOUT_MINUTES);
                case COMPLETED, FAILED -> TimeUnit.MINUTES.toNanos(COMPLETED_RETENTION_MINUTES);
                default -> Long.MAX_VALUE;
            };
            varExp.setExpiresAfter(ctx.taskId, nanos, TimeUnit.NANOSECONDS);
        });
    }

    /**
     * 处理 Agent 事件（Stream 化，每个事件触发状态迁移）。
     */
    private void onAgentEvent(TaskContext ctx, AgentEvent event) {
        switch (event) {
            case TextBlockDeltaEvent tbd -> {
                if (tbd.getDelta() != null) {
                    ctx.result.append(tbd.getDelta());
                    ctx.eventSink.tryEmitNext(new TaskEvent(
                            ctx.taskId, EVENT_TEXT_DELTA, tbd.getDelta()));
                }
            }
            case RequireUserConfirmEvent rce -> {
                List<PendingToolCall> pendingCalls = new ArrayList<>();
                for (ToolUseBlock tu : rce.getToolCalls()) {
                    pendingCalls.add(new PendingToolCall(
                            tu.getId(), tu.getName(),
                            tu.getInput() != null ? tu.getInput() : Map.of()
                    ));
                }
                ctx.status = TaskStatus.WAITING_CONFIRM;
                ctx.pendingToolCalls = pendingCalls;
                ctx.replyId = rce.getReplyId();
                refreshExpiry(ctx); // 启动确认超时计时

                log.info("[AsyncTask] Task {} requires confirmation for {} tool(s)",
                        ctx.taskId, pendingCalls.size());
                ctx.eventSink.tryEmitNext(new TaskEvent(
                        ctx.taskId, EVENT_NEED_CONFIRM,
                        Map.of("taskId", ctx.taskId, "pendingToolCalls", pendingCalls)
                ));
            }
            default -> {
                // 其他事件忽略
            }
        }
    }

    /**
     * 处理 Agent 执行错误。
     */
    private void onAgentError(TaskContext ctx, Throwable error) {
        log.error("[AsyncTask] Task {} failed", ctx.taskId, error);
        ctx.status = TaskStatus.FAILED;
        ctx.error = error.getMessage();
        refreshExpiry(ctx); // FAILED → 保留 N 分钟后自动清理
        // 任务结束，释放重量级引用
        ctx.release();
        ctx.eventSink.tryEmitNext(new TaskEvent(
                ctx.taskId, EVENT_FAILED,
                Map.of("taskId", ctx.taskId, "error", error.getMessage())
        ));
        ctx.eventSink.tryEmitComplete();
    }

    /**
     * 处理 Agent 执行完成。
     */
    private void onAgentComplete(TaskContext ctx) {
        // 如果当前状态是 WAITING_CONFIRM，说明事件流结束但任务暂停了
        if (ctx.status == TaskStatus.WAITING_CONFIRM) {
            log.info("[AsyncTask] Task {} paused, waiting for confirmation", ctx.taskId);
            return; // 不完成 Sink，等待 confirmAndResume 或 Caffeine 超时驱逐
        }

        ctx.status = TaskStatus.COMPLETED;
        refreshExpiry(ctx); // COMPLETED → 保留 N 分钟后自动清理
        // 任务结束，释放重量级引用
        ctx.release();
        log.info("[AsyncTask] Task {} completed", ctx.taskId);
        ctx.eventSink.tryEmitNext(new TaskEvent(
                ctx.taskId, EVENT_COMPLETED,
                Map.of("taskId", ctx.taskId, "result", ctx.result.toString())
        ));
        ctx.eventSink.tryEmitComplete();
    }

    // ==================== Caffeine 动态 TTL 策略 ====================

    /**
     * 按任务状态动态计算过期时间：
     * <ul>
     *   <li>RUNNING / RESUMING → 不过期（返回 MAX_VALUE）</li>
     *   <li>WAITING_CONFIRM → {@value CONFIRM_TIMEOUT_MINUTES} 分钟</li>
     *   <li>COMPLETED / FAILED → {@value COMPLETED_RETENTION_MINUTES} 分钟</li>
     * </ul>
     */
    private static class TaskExpiry implements Expiry<String, TaskContext> {

        @Override
        public @NonNegative long expireAfterCreate(String key, TaskContext ctx, long currentTime) {
            return Long.MAX_VALUE; // 创建时不过期，由 update 决定
        }

        @Override
        public @NonNegative long expireAfterUpdate(String key, TaskContext ctx,
                                                    long currentTime, @NonNegative long currentDuration) {
            return switch (ctx.status) {
                case WAITING_CONFIRM -> TimeUnit.MINUTES.toNanos(CONFIRM_TIMEOUT_MINUTES);
                case COMPLETED, FAILED -> TimeUnit.MINUTES.toNanos(COMPLETED_RETENTION_MINUTES);
                default -> Long.MAX_VALUE; // RUNNING / RESUMING 不过期
            };
        }

        @Override
        public @NonNegative long expireAfterRead(String key, TaskContext ctx,
                                                  long currentTime, @NonNegative long currentDuration) {
            // 读取不改变过期策略，但对终态任务刷新访问计时
            return currentDuration;
        }
    }

    // ==================== 内部任务上下文 ====================

    /**
     * 任务上下文 — 持有任务执行期间的所有可变状态。
     *
     * <p>使用 Sinks.Many 作为事件总线，支持多订阅者（SSE + 回调 + 日志）。
     * 任务结束后通过 {@link #release()} 释放重量级引用，减少内存占用。
     */
    private static class TaskContext {
        final String taskId;

        /** 事件总线 — 支持多订阅者 */
        final Sinks.Many<TaskEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer();

        volatile TaskStatus status = TaskStatus.RUNNING;
        volatile List<PendingToolCall> pendingToolCalls;
        final StringBuilder result = new StringBuilder();
        volatile String error;
        volatile String replyId;
        volatile boolean confirmed;

        // 重量级引用 — 任务结束后置 null 帮助 GC
        volatile AgentScopeAgentAdapter agent;
        volatile AgentContext context;

        TaskContext(String taskId, AgentScopeAgentAdapter agent, AgentContext context) {
            this.taskId = taskId;
            this.agent = agent;
            this.context = context;
        }

        /**
         * 释放重量级引用（agent、context），任务终态后调用。
         * result 保留供后续查询，pendingToolCalls 也保留。
         */
        void release() {
            this.agent = null;
            this.context = null;
        }    }
}
