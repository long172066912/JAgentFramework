package com.jrl.ai.agent.demo.tagging.model;

/**
 * 回执方式枚举 — 定义任务处理完成后的回执通道类型。
 */
public enum CallbackType {

    /** MQ 消息队列回执 */
    MQ("mq"),

    /** HTTP 回调 */
    HTTP("http");

    private final String value;

    CallbackType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 从字符串解析为枚举（忽略大小写）。
     *
     * @param value 字符串值（mq / http）
     * @return 对应的枚举值
     * @throws IllegalArgumentException 无效的回执类型
     */
    public static CallbackType fromString(String value) {
        if (value == null || value.isBlank()) {
            return MQ; // 默认 MQ
        }
        for (CallbackType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的回执类型: " + value + "，支持: mq, http");
    }

    /**
     * 校验回执地址是否有效。
     *
     * @param address 回执地址
     * @return true 如果地址对该类型有效
     */
    public boolean validateAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        return switch (this) {
            case MQ -> address.matches("^[a-zA-Z][a-zA-Z0-9_]*$"); // topic 格式：字母开头，字母数字下划线
            case HTTP -> address.startsWith("http://") || address.startsWith("https://");
        };
    }

    /**
     * 获取地址格式错误的提示信息。
     *
     * @param address 无效地址
     * @return 错误提示信息
     */
    public String getAddressErrorMessage(String address) {
        return switch (this) {
            case MQ -> "MQ topic 格式无效: '%s'，应为字母开头的字母数字下划线组合".formatted(address);
            case HTTP -> "HTTP URL 格式无效: '%s'，应以 http:// 或 https:// 开头".formatted(address);
        };
    }
}
