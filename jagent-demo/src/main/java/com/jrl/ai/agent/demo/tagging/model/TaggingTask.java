package com.jrl.ai.agent.demo.tagging.model;

import java.util.Map;

/**
 * MQ 打标任务投递协议 — 对应 AI Agent 交互协议设计。
 *
 * @param taskId           任务唯一标识
 * @param taskType         任务类型（mark_tag）
 * @param payloadType      数据类型（product / task / post）
 * @param payload          具体内容（商品 ID/用户 ID/内容数据）
 * @param requiredTagCount 要求的标签数量（0 表示使用默认值）
 * @param callbackType     回执方式（MQ / HTTP）
 * @param callbackAddress  回执地址（MQ 时为 topic，HTTP 时为 URL）
 * @param priority         优先级（1 紧急 / 2 普通 / 3 低）
 * @param remark           备注说明
 * @param timestamp        投递时间戳
 */
public record TaggingTask(
        String taskId,
        String taskType,
        String payloadType,
        Map<String, Object> payload,
        int requiredTagCount,
        CallbackType callbackType,
        String callbackAddress,
        int priority,
        String remark,
        long timestamp
) {
    /** 任务类型常量 */
    public static final String TYPE_MARK_TAG = "mark_tag";
    /** 默认回执地址 */
    public static final String DEFAULT_CALLBACK_ADDRESS = "tagging_callback";

    /**
     * 快速构建一个打标任务（使用默认标签数量，不回执）。
     */
    public static TaggingTask markTag(String taskId, String payloadType,
                                       Map<String, Object> payload, String remark) {
        return markTag(taskId, payloadType, payload, 0, CallbackType.NONE, null, remark);
    }

    /**
     * 快速构建一个打标任务（指定标签数量和回执方式）。
     *
     * @throws IllegalArgumentException 回执地址格式无效时抛出
     */
    public static TaggingTask markTag(String taskId, String payloadType,
                                       Map<String, Object> payload, int requiredTagCount,
                                       CallbackType callbackType, String callbackAddress, String remark) {
        // 解析回执类型和地址
        CallbackType type = callbackType != null ? callbackType : CallbackType.NONE;
        String address = callbackAddress != null ? callbackAddress : "";

        // 校验地址格式（仅当需要回执时）
        if (type != CallbackType.NONE && !type.validateAddress(address)) {
            throw new IllegalArgumentException(type.getAddressErrorMessage(address));
        }

        return new TaggingTask(
                taskId, TYPE_MARK_TAG, payloadType, payload,
                requiredTagCount, type, address,
                2, remark, System.currentTimeMillis()
        );
    }

    /**
     * 快速构建一个打标任务（字符串类型回执方式，兼容旧接口）。
     */
    public static TaggingTask markTag(String taskId, String payloadType,
                                       Map<String, Object> payload, int requiredTagCount,
                                       String callbackTypeStr, String callbackAddress, String remark) {
        return markTag(taskId, payloadType, payload, requiredTagCount,
                CallbackType.fromString(callbackTypeStr), callbackAddress, remark);
    }
}
