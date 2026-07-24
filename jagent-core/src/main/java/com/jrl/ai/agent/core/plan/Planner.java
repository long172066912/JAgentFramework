package com.jrl.ai.agent.core.plan;

/**
 * 计划器 — 根据目标生成执行计划
 */
public interface Planner {

    /**
     * 根据目标生成计划
     */
    Plan createPlan(String agentId, String goal);

    /**
     * 根据执行反馈调整计划
     */
    Plan adjustPlan(Plan currentPlan, String feedback);
}
