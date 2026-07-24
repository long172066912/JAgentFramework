package com.jrl.ai.agent.core.task;

import com.jrl.ai.agent.core.task.contract.TaskResponse;
import com.jrl.ai.agent.core.task.contract.TokenUsage;

import java.util.Map;

/**
 * 任务结果 — 可转换为 TaskResponse 输出契约
 */
public record TaskResult(
        String taskId,
        String sessionId,
        TaskStatus status,
        String resultType,
        Map<String, Object> result,
        TokenUsage usage,
        Throwable error,
        long durationMs
) {

    public static TaskResult success(String taskId, String sessionId,
                                     String resultType, Map<String, Object> result,
                                     TokenUsage usage, long durationMs) {
        return new TaskResult(taskId, sessionId, TaskStatus.COMPLETED,
                resultType, result, usage, null, durationMs);
    }

    public static TaskResult failure(String taskId, String sessionId,
                                     String errorCode, String errorMessage, long durationMs) {
        return new TaskResult(taskId, sessionId, TaskStatus.FAILED,
                null, Map.of(), null,
                new RuntimeException(errorCode + ": " + errorMessage), durationMs);
    }

    public boolean isSuccess() {
        return status == TaskStatus.COMPLETED;
    }

    /**
     * 转换为传输无关的输出契约
     */
    public TaskResponse toResponse() {
        if (isSuccess()) {
            return TaskResponse.success(taskId, sessionId, null,
                    resultType, result, usage, durationMs);
        }
        String errorCode = error != null ? error.getMessage() : "UNKNOWN_ERROR";
        return TaskResponse.failure(taskId, sessionId, null,
                errorCode, error != null ? error.getMessage() : null, durationMs);
    }
}
