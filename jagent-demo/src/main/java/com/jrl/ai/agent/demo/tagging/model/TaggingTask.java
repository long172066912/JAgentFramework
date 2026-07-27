package com.jrl.ai.agent.demo.tagging.model;

import java.util.Map;

/**
 * MQ 打标任务投递协议 — 对应 AI Agent 交互协议设计。
 *
 * @param taskId        任务唯一标识
 * @param taskType      任务类型（mark_tag）
 * @param payloadType   数据类型（product / task / post）
 * @param payload       具体内容（商品 ID/用户 ID/内容数据）
 * @param priority      优先级（1 紧急 / 2 普通 / 3 低）
 * @param remark        备注说明
 * @param timestamp     投递时间戳
 * @param callbackTopic 处理完后回执的 Topic
 */
public record TaggingTask(
        String taskId,
        String taskType,
        String payloadType,
        Map<String, Object> payload,
        int priority,
        String remark,
        long timestamp,
        String callbackTopic
) {
    /** 任务类型常量 */
    public static final String TYPE_MARK_TAG = "mark_tag";

    /**
     * 快速构建一个打标任务。
     */
    public static TaggingTask markTag(String taskId, String payloadType,
                                       Map<String, Object> payload, String remark) {
        return new TaggingTask(
                taskId, TYPE_MARK_TAG, payloadType, payload,
                2, remark, System.currentTimeMillis(), "tagging_callback"
        );
    }
}
