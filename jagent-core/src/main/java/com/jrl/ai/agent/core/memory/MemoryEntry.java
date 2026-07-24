package com.jrl.ai.agent.core.memory;

import java.time.Instant;

/**
 * 记忆条目
 */
public record MemoryEntry(
        String agentId,
        String key,
        String content,
        MemoryType type,
        Instant createdAt,
        Instant updatedAt,
        int accessCount
) {

    public static MemoryEntry of(String agentId, String key, String content, MemoryType type) {
        return new MemoryEntry(agentId, key, content, type, Instant.now(), Instant.now(), 0);
    }
}
