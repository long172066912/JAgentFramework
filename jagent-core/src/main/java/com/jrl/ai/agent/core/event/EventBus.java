package com.jrl.ai.agent.core.event;

/**
 * 事件总线 — 事件的发布与订阅中枢
 */
public interface EventBus {

    /**
     * 发布事件
     */
    void publish(AgentEvent event);

    /**
     * 注册监听器
     */
    void subscribe(EventListener listener);

    /**
     * 取消注册
     */
    void unsubscribe(EventListener listener);
}
