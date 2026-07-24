package com.jrl.ai.agent.core.event;

/**
 * 事件监听器 — 订阅并处理 Agent 事件
 */
public interface EventListener {

    /**
     * 处理事件
     */
    void onEvent(AgentEvent event);

    /**
     * 是否关心该事件类型（用于过滤）
     */
    default boolean accepts(String eventType) {
        return true;
    }
}
