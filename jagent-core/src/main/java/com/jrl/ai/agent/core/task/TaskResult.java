package com.jrl.ai.agent.core.task;

/**
 * 任务结果
 */
public record TaskResult(
        String taskId,
        TaskStatus status,
        String output,
        Throwable error,
        long durationMs
) {

    public static TaskResult success(String taskId, String output, long durationMs) {
        return new TaskResult(taskId, TaskStatus.COMPLETED, output, null, durationMs);
    }

    public static TaskResult failure(String taskId, Throwable error, long durationMs) {
        return new TaskResult(taskId, TaskStatus.FAILED, null, error, durationMs);
    }

    public boolean isSuccess() {
        return status == TaskStatus.COMPLETED;
    }
}
