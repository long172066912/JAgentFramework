package com.jrl.ai.agent.core.context;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时上下文 — 贯穿整个请求生命周期的数据载体。
 *
 * <p>承载会话标识、用户标识以及可扩展的自定义属性，
 * 在 Agent 执行、Skill 调用、反馈采集等环节中透传。
 * 通过 {@link Builder} 构建，线程安全。
 *
 * @see Agent#execute
 */
public class AgentContext {

    /** 会话唯一标识，用于关联同一轮对话 */
    private final String sessionId;
    /** 用户唯一标识，用于多租户隔离和个性化 */
    private final String userId;
    /** 可扩展属性集合，支持并发读写 */
    private final Map<String, Object> attributes;

    private AgentContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.attributes = new ConcurrentHashMap<>(builder.attributes);
    }

    /** 获取会话 ID。 */
    public String sessionId() { return sessionId; }
    /** 获取用户 ID。 */
    public String userId() { return userId; }

    /**
     * 获取指定键的扩展属性。
     *
     * @param key 属性键
     * @param <T> 属性值类型
     * @return 属性值，不存在时返回 {@link Optional#empty()}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) attributes.get(key));
    }

    /**
     * 设置扩展属性。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取所有扩展属性的不可变副本。
     *
     * @return 不可变的属性 Map
     */
    public Map<String, Object> attributes() {
        return Map.copyOf(attributes);
    }

    /** 创建新的 Builder。 */
    public static Builder builder() { return new Builder(); }

    /**
     * AgentContext 构建器，支持流式 API。
     */
    public static class Builder {
        private String sessionId;
        private String userId;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        /** 设置会话 ID。 */
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        /** 设置用户 ID。 */
        public Builder userId(String userId) { this.userId = userId; return this; }
        /** 添加扩展属性。 */
        public Builder attribute(String key, Object value) { attributes.put(key, value); return this; }
        /** 构建不可变的 AgentContext 实例。 */
        public AgentContext build() { return new AgentContext(this); }
    }
}
