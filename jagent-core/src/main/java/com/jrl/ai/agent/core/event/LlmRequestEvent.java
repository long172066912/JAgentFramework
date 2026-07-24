package com.jrl.ai.agent.core.event;

import java.time.Instant;

public record LlmRequestEvent(
        Instant timestamp,
        String agentId,
        String modelId,
        int promptTokens
) implements AgentEvent {
    @Override
    public String type() { return "llm.request"; }
}
