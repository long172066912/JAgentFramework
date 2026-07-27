package com.jrl.ai.agent.core.plan;

/**
 * 目标 — 规划系统的驱动单元。
 *
 * <p>灵感来源于 GOAP（Goal-Oriented Action Planning），
 * Goal 描述 Agent 期望达到的状态，由 Planner 负责制定达成路径。
 * 与简单的任务描述不同，Goal 强调"要什么"而非"怎么做"，
 * 允许规划器动态组合 Action 来达成目标。
 *
 * @see Planner
 */
public interface Goal {

    /**
     * 获取目标名称。
     *
     * @return 目标唯一标识
     */
    String name();

    /**
     * 获取目标描述（供 LLM 理解目标含义）。
     *
     * @return 目标描述文本
     */
    String description();

    /**
     * 判断目标是否已达成。
     *
     * <p>在每步 Action 执行后重新评估，用于判断是否需要继续规划。
     *
     * @param state 当前世界状态
     * @return 若目标已达成则返回 {@code true}
     */
    boolean isAchieved(Object state);

    /**
     * 获取目标优先级（数值越大优先级越高）。
     *
     * <p>当存在多个待达成目标时，Planner 按优先级排序处理。
     * 默认返回 0（普通优先级）。
     *
     * @return 优先级数值
     */
    default int priority() {
        return 0;
    }
}
