package com.jrl.ai.agent.core.task;

import java.time.Instant;
import java.util.Map;

/**
 * 任务 — Agent 处理的工作单元
 */
public record Task(
        String id,
        String name,
        String description,
        String input,
        TaskStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> metadata
) {

    public static Task create(String name, String input) {
        return new Task(
                java.util.UUID.randomUUID().toString(),
                name,
                null,
                input,
                TaskStatus.PENDING,
                Instant.now(),
                null,
                null,
                Map.of()
        );
    }
}
