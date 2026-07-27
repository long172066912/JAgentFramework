package com.jrl.ai.agent.core.task.contract;

import java.util.Map;

/**
 * 任务响应 — 与传输方式无关的任务输出契约
 * <p>
 * 框架处理完任务后产出 TaskResponse，由传输层适配器负责转换为具体协议的消息。
 * 支持终态（success/fail/timeout）和中间状态（processing）。
 */
public record TaskResponse(
        /** 关联原任务 ID */
        String taskId,
        /** 会话 ID */
        String sessionId,
        /** 任务类型 */
        String taskType,
        /** 状态 */
        ResponseStatus status,
        /** 进度百分比（0-100，processing 时有值） */
        int progress,
        /** 结果类型标识 */
        String resultType,
        /** 结构化处理结果 */
        Map<String, Object> result,
        /** Token 消耗统计 */
        TokenUsage usage,
        /** 错误码 */
        String errorCode,
        /** 错误信息 */
        String errorMessage,
        /** 处理耗时 ms */
        long processTime,
        /** 时间戳 */
        long timestamp
) {

    /**
     * 创建处理中响应（中间状态，可多次发送）。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话 ID
     * @param taskType  任务类型
     * @param progress  进度百分比（0-100）
     * @return 处理中的 TaskResponse
     */
    public static TaskResponse processing(String taskId, String sessionId, String taskType, int progress) {
        return new TaskResponse(taskId, sessionId, taskType, ResponseStatus.PROCESSING,
                progress, null, Map.of(), null, null, null, 0, System.currentTimeMillis());
    }

    /**
     * 创建成功响应。
     *
     * @param taskId     任务 ID
     * @param sessionId  会话 ID
     * @param taskType   任务类型
     * @param resultType 结果类型
     * @param result     结构化结果
     * @param usage      Token 消耗统计
     * @param processTime 处理耗时（ms）
     * @return 成功的 TaskResponse
     */
    public static TaskResponse success(String taskId, String sessionId, String taskType,
                                       String resultType, Map<String, Object> result,
                                       TokenUsage usage, long processTime) {
        return new TaskResponse(taskId, sessionId, taskType, ResponseStatus.SUCCESS,
                100, resultType, result, usage, null, null, processTime, System.currentTimeMillis());
    }

    /**
     * 创建失败响应。
     *
     * @param taskId       任务 ID
     * @param sessionId    会话 ID
     * @param taskType     任务类型
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @param processTime  处理耗时（ms）
     * @return 失败的 TaskResponse
     */
    public static TaskResponse failure(String taskId, String sessionId, String taskType,
                                       String errorCode, String errorMessage, long processTime) {
        return new TaskResponse(taskId, sessionId, taskType, ResponseStatus.FAIL,
                0, null, Map.of(), null, errorCode, errorMessage, processTime, System.currentTimeMillis());
    }

    /**
     * 创建超时响应。
     *
     * @param taskId      任务 ID
     * @param sessionId   会话 ID
     * @param taskType    任务类型
     * @param processTime 处理耗时（ms）
     * @return 超时的 TaskResponse
     */
    public static TaskResponse timeout(String taskId, String sessionId, String taskType, long processTime) {
        return new TaskResponse(taskId, sessionId, taskType, ResponseStatus.TIMEOUT,
                0, null, Map.of(), null, AgentErrorCode.TASK_TIMEOUT, "任务执行超时", processTime, System.currentTimeMillis());
    }
}
