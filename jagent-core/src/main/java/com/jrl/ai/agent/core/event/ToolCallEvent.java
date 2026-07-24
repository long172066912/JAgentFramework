package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record ToolCallEvent(
        Instant timestamp,
        String agentId,
        String toolName,
        String callId,
        String arguments
) implements AgentEvent {
    @Override
    public String type() { return "tool.call"; }
}
