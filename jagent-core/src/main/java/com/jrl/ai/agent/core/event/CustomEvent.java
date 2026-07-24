package com.jrl.ai.agent.core.event;

import java.time.Instant;

/**
 * 自定义事件 — 用户可扩展的事件类型
 */
public record CustomEvent(
        Instant timestamp,
        String agentId,
        String type,
        Object payload
) implements AgentEvent {
}
