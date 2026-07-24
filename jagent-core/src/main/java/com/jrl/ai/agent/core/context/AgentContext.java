package com.jrl.ai.agent.core.context;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时上下文 — 贯穿整个请求生命周期的数据载体
 */
public class AgentContext {

    private final String sessionId;
    private final String userId;
    private final Map<String, Object> attributes;

    private AgentContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.attributes = new ConcurrentHashMap<>(builder.attributes);
    }

    public String sessionId() { return sessionId; }
    public String userId() { return userId; }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) attributes.get(key));
    }

    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    public Map<String, Object> attributes() {
        return Map.copyOf(attributes);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sessionId;
        private String userId;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder attribute(String key, Object value) { attributes.put(key, value); return this; }
        public AgentContext build() { return new AgentContext(this); }
    }
}
