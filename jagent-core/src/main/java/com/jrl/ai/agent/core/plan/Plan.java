package com.jrl.ai.agent.core.plan;

/**
 * 计划 — 由 Planner 生成的动作序列，描述如何达成目标。
 *
 * <p>计划是动态生成的（非硬编码），每步执行后可根据新信息重新规划。
 * 这构成了一个 OODA 循环（观察-定向-决策-行动）。
 *
 * @see Planner
 * @see Goal
 */
public record Plan(
        /** 计划唯一标识 */
        String id,
        /** 关联的目标 */
        Goal goal,
        /** 计划步骤列表（按执行顺序排列） */
        java.util.List<PlanStep> steps,
        /** 计划当前状态 */
        PlanStatus status
) {

    /**
     * 为指定目标创建待执行计划。
     *
     * @param goal  目标
     * @param steps 计划步骤
     * @return 新建的计划实例
     */
    public static Plan create(Goal goal, java.util.List<PlanStep> steps) {
        return new Plan(
                java.util.UUID.randomUUID().toString(),
                goal, java.util.List.copyOf(steps), PlanStatus.CREATED
        );
    }
}
