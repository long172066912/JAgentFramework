package com.jrl.ai.agent.core.plan;

import com.jrl.ai.agent.core.context.AgentContext;

/**
 * 规划器 — 根据目标动态生成执行计划。
 *
 * <p>受 GOAP（Goal-Oriented Action Planning）启发，Planner 负责：
 * <ol>
 *   <li>分析当前状态与目标之间的差距</li>
 *   <li>动态规划达成目标的动作序列</li>
 *   <li>在每步执行后评估是否需要重新规划</li>
 * </ol>
 *
 * <p>与硬编码的执行流程不同，Planner 允许 Agent 在面对新情况时
 * 自适应调整策略。适配层可对接 AgentScope 的 Plan Mode 实现。
 *
 * @see Goal
 * @see Plan
 */
public interface Planner {

    /**
     * 为指定目标制定执行计划。
     *
     * @param goal    待达成的目标
     * @param context 运行时上下文
     * @param state   当前世界状态（供条件评估使用）
     * @return 生成的执行计划，若无法规划则返回 {@link java.util.Optional#empty()}
     */
    java.util.Optional<Plan> plan(Goal goal, AgentContext context, Object state);

    /**
     * 评估当前计划是否需要重新规划。
     *
     * <p>在每步执行后调用，检查环境变化是否导致当前计划失效。
     * 若返回 {@code true}，调用方应重新调用 {@link #plan} 生成新计划。
     *
     * @param currentPlan 当前执行中的计划
     * @param state       最新世界状态
     * @return 若需要重新规划则返回 {@code true}
     */
    boolean needsReplan(Plan currentPlan, Object state);
}
