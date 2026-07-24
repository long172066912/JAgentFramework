package com.jrl.ai.agent.core.plan;

/**
 * 计划步骤
 */
public record PlanStep(
        int order,
        String description,
        String skillName,
        StepStatus status,
        String result
) {

    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }
}
