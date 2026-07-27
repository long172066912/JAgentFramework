package com.jrl.ai.agent.core.plan;

/**
 * 计划步骤 — 计划中的单个执行单元。
 *
 * <p>每个步骤对应一个具体的动作，包含描述、前置条件和后置条件。
 * GOAP 风格的规划器通过组合步骤来达成目标。
 *
 * @see Plan
 */
public record PlanStep(
        /** 步骤唯一标识 */
        String id,
        /** 步骤描述（供 LLM 理解动作含义） */
        String description,
        /** 步骤序号（从 0 开始） */
        int order,
        /** 前置条件描述（执行此步骤前需满足的状态） */
        String precondition,
        /** 后置条件描述（执行此步骤后产生的状态变化） */
        String postcondition,
        /** 步骤当前状态 */
        StepStatus status
) {

    /**
     * 步骤执行状态。
     */
    public enum StepStatus {
        /** 待执行 */
        PENDING,
        /** 执行中 */
        RUNNING,
        /** 已成功 */
        SUCCEEDED,
        /** 已失败 */
        FAILED,
        /** 已跳过 */
        SKIPPED
    }

    /**
     * 创建一个待执行的步骤。
     *
     * @param description  步骤描述
     * @param order        步骤序号
     * @param precondition 前置条件
     * @param postcondition 后置条件
     * @return 新建的步骤实例
     */
    public static PlanStep pending(String description, int order,
                                   String precondition, String postcondition) {
        return new PlanStep(
                java.util.UUID.randomUUID().toString(),
                description, order, precondition, postcondition, StepStatus.PENDING
        );
    }
}
