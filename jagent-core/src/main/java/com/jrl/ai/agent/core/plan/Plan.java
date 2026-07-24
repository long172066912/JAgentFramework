package com.jrl.ai.agent.core.plan;

import java.time.Instant;
import java.util.List;

/**
 * 计划 — Agent 的任务分解与执行规划
 */
public record Plan(
        String id,
        String agentId,
        String goal,
        List<PlanStep> steps,
        PlanStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static Plan create(String agentId, String goal, List<PlanStep> steps) {
        return new Plan(
                java.util.UUID.randomUUID().toString(),
                agentId,
                goal,
                steps,
                PlanStatus.CREATED,
                Instant.now(),
                Instant.now()
        );
    }
}
