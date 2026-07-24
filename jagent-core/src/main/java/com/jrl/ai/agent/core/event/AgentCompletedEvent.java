package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record AgentCompletedEvent(
        Instant timestamp,
        String agentId,
        String taskId,
        String output,
        long durationMs
) implements AgentEvent {
    @Override
    public String type() { return "agent.completed"; }
}
