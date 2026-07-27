package com.jrl.ai.agent.demo.tagging.model;

import java.util.Map;

/**
 * MQ 打标任务回执协议 — 对应 AI Agent 任务处理回执设计。
 *
 * @param taskId      关联原任务 ID
 * @param taskType    任务类型
 * @param payloadType 数据类型
 * @param status      处理状态（success / fail）
 * @param message     失败原因（成功时为空）
 * @param payload     附加信息（标签列表等）
 * @param processTime 处理耗时（ms）
 * @param timestamp   完成时间戳
 */
public record TaggingCallback(
        String taskId,
        String taskType,
        String payloadType,
        String status,
        String message,
        Map<String, Object> payload,
        long processTime,
        long timestamp
) {
    /** 成功状态标识 */
    public static final String STATUS_SUCCESS = "success";
    /** 失败状态标识 */
    public static final String STATUS_FAIL = "fail";

    /**
     * 构建成功回执。
     */
    public static TaggingCallback success(TaggingTask task, Map<String, Object> payload, long processTime) {
        return new TaggingCallback(
                task.taskId(), task.taskType(), task.payloadType(),
                STATUS_SUCCESS, "", payload, processTime, System.currentTimeMillis()
        );
    }

    /**
     * 构建失败回执。
     */
    public static TaggingCallback fail(TaggingTask task, String message, long processTime) {
        return new TaggingCallback(
                task.taskId(), task.taskType(), task.payloadType(),
                STATUS_FAIL, message, Map.of(), processTime, System.currentTimeMillis()
        );
    }
}
