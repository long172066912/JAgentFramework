package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record AgentStartedEvent(
        Instant timestamp,
        String agentId,
        String taskId,
        String input
) implements AgentEvent {
    @Override
    public String type() { return "agent.started"; }
}
