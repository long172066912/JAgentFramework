package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record ToolResultEvent(
        Instant timestamp,
        String agentId,
        String toolName,
        String callId,
        String result,
        boolean isError,
        long durationMs
) implements AgentEvent {
    @Override
    public String type() { return "tool.result"; }
}
