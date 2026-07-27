package com.jrl.ai.agent.core.task;

import com.jrl.ai.agent.core.task.contract.TaskResponse;
import com.jrl.ai.agent.core.task.contract.TokenUsage;

import java.util.Map;

/**
 * 任务结果 — Agent 执行后的内部结果对象。
 *
 * <p>封装任务执行的完整状态信息，可通过 {@link #toResponse()}
 * 转换为传输无关的 {@link com.jrl.ai.agent.core.task.contract.TaskResponse} 输出契约。
 *
 * @see Task
 */
public record TaskResult(
        /** 关联的任务 ID */
        String taskId,
        /** 会话 ID */
        String sessionId,
        /** 任务最终状态 */
        TaskStatus status,
        /** 结果类型标识（如 text/json/image） */
        String resultType,
        /** 结构化处理结果 */
        Map<String, Object> result,
        /** Token 消耗统计 */
        TokenUsage usage,
        /** 执行链路追踪 */
        ExecutionTrace trace,
        /** 异常信息（失败时非空） */
        Throwable error,
        /** 执行耗时（毫秒） */
        long durationMs
) {

    /**
     * 创建成功结果。
     *
     * @param taskId     任务 ID
     * @param sessionId  会话 ID
     * @param resultType 结果类型
     * @param result     结构化结果
     * @param usage      Token 消耗
     * @param durationMs 执行耗时
     * @return 成功的 TaskResult
     */
    public static TaskResult success(String taskId, String sessionId,
                                     String resultType, Map<String, Object> result,
                                     TokenUsage usage, long durationMs) {
        return new TaskResult(taskId, sessionId, TaskStatus.COMPLETED,
                resultType, result, usage, null, null, durationMs);
    }

    /**
     * 创建失败结果。
     *
     * @param taskId       任务 ID
     * @param sessionId    会话 ID
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @param durationMs   执行耗时
     * @return 失败的 TaskResult
     */
    public static TaskResult failure(String taskId, String sessionId,
                                     String errorCode, String errorMessage, long durationMs) {
        return new TaskResult(taskId, sessionId, TaskStatus.FAILED,
                null, Map.of(), null, null,
                new RuntimeException(errorCode + ": " + errorMessage), durationMs);
    }

    /** 判断任务是否成功完成。 */
    public boolean isSuccess() {
        return status == TaskStatus.COMPLETED;
    }

    /**
     * 设置执行链路追踪，返回新的 TaskResult。
     *
     * @param trace 执行链路追踪
     * @return 携带 trace 的新 TaskResult
     */
    public TaskResult withTrace(ExecutionTrace trace) {
        return new TaskResult(taskId, sessionId, status, resultType, result, usage, trace, error, durationMs);
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
