package com.jrl.ai.agent.core.event;

import java.time.Instant;

/**
 * 事件 — 框架内所有可观测行为的基类
 */
public sealed interface AgentEvent permits
        AgentStartedEvent,
        AgentCompletedEvent,
        AgentErrorEvent,
        ToolCallEvent,
        ToolResultEvent,
        LlmRequestEvent,
        LlmResponseEvent,
        MemoryEvent,
        PlanEvent,
        CustomEvent {

    /**
     * 事件发生时间
     */
    Instant timestamp();

    /**
     * 事件来源 Agent ID
     */
    String agentId();

    /**
     * 事件类型名称
     */
    String type();
}
