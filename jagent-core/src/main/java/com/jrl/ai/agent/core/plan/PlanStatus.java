package com.jrl.ai.agent.core.plan;

/**
 * 计划状态 — 描述计划在生命周期中的各个阶段。
 *
 * @see Plan
 */
public enum PlanStatus {
    /** 已创建，尚未开始执行 */
    CREATED,
    /** 执行中 */
    EXECUTING,
    /** 等待外部输入（人工确认等） */
    WAITING_INPUT,
    /** 已成功完成（终态） */
    COMPLETED,
    /** 执行失败（终态） */
    FAILED,
    /** 已取消（终态） */
    CANCELLED,
    /** 需要重新规划（当前计划不再适用） */
    NEEDS_REPLAN
}
