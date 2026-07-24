package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record PlanEvent(
        Instant timestamp,
        String agentId,
        String planId,
        String action,
        String description
) implements AgentEvent {
    @Override
    public String type() { return "plan." + action; }
}
