package com.jrl.ai.agent.core.task;

import com.jrl.ai.agent.core.task.contract.TaskRequest;

import java.time.Instant;

/**
 * 任务 — Agent 处理的工作单元
 * <p>
 * Task = TaskRequest（输入契约） + 执行状态
 */
public record Task(
        /** 原始请求 */
        TaskRequest request,
        /** 执行状态 */
        TaskStatus status,
        /** 创建时间 */
        Instant createdAt,
        /** 开始执行时间 */
        Instant startedAt,
        /** 完成时间 */
        Instant completedAt
) {

    /** 任务 ID（快捷访问） */
    public String id() { return request.taskId(); }

    /** 任务类型（快捷访问） */
    public String type() { return request.taskType(); }

    /**
     * 从请求创建任务。
     *
     * @param request 任务请求
     * @return 初始状态为 PENDING 的新任务
     */
    public static Task fromRequest(TaskRequest request) {
        return new Task(
                request,
                TaskStatus.PENDING,
                Instant.ofEpochMilli(request.timestamp()),
                null,
                null
        );
    }

    /**
     * 标记为开始执行。
     *
     * @return 状态为 RUNNING 的新 Task
     */
    public Task start() {
        return new Task(request, TaskStatus.RUNNING, createdAt, Instant.now(), null);
    }

    /**
     * 标记为完成。
     *
     * @return 状态为 COMPLETED 的新 Task
     */
    public Task complete() {
        return new Task(request, TaskStatus.COMPLETED, createdAt, startedAt, Instant.now());
    }

    /**
     * 标记为失败。
     *
     * @return 状态为 FAILED 的新 Task
     */
    public Task fail() {
        return new Task(request, TaskStatus.FAILED, createdAt, startedAt, Instant.now());
    }

    /**
     * 标记为取消。
     *
     * @return 状态为 CANCELLED 的新 Task
     */
    public Task cancel() {
        return new Task(request, TaskStatus.CANCELLED, createdAt, startedAt, Instant.now());
    }

    /**
     * 执行耗时（ms）。
     *
     * @return 开始到完成的时间差，未开始或未完成时返回 0
     */
    public long durationMs() {
        if (startedAt == null || completedAt == null) return 0;
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
