package com.jrl.ai.agent.core.monitor;

import com.jrl.ai.agent.core.event.AgentEvent;

/**
 * 监控指标收集器
 */
public interface MetricsCollector {

    /**
     * 记录事件
     */
    void record(AgentEvent event);

    /**
     * 记录自定义指标
     */
    void gauge(String name, double value);

    /**
     * 计数器递增
     */
    void counter(String name, long delta);

    /**
     * 记录耗时
     */
    void timer(String name, long durationMs);
}
