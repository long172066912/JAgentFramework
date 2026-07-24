package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record AgentErrorEvent(
        Instant timestamp,
        String agentId,
        String taskId,
        Throwable error
) implements AgentEvent {
    @Override
    public String type() { return "agent.error"; }
}
