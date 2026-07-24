package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record LlmResponseEvent(
        Instant timestamp,
        String agentId,
        String modelId,
        int completionTokens,
        long durationMs
) implements AgentEvent {
    @Override
    public String type() { return "llm.response"; }
}
