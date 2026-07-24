package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record MemoryEvent(
        Instant timestamp,
        String agentId,
        String action,
        String key
) implements AgentEvent {
    @Override
    public String type() { return "memory." + action; }
}
