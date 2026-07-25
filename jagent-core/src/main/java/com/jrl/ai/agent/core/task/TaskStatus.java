package com.jrl.ai.agent.core.task;

/**
 * 任务状态 — 描述任务在生命周期中的各个阶段。
 *
 * @see Task
 * @see TaskResult
 */
public enum TaskStatus {
    /** 待处理，尚未开始执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 等待用户输入（人工介入） */
    WAITING_INPUT,
    /** 执行成功完成（终态） */
    COMPLETED,
    /** 执行失败（终态） */
    FAILED,
    /** 已取消（终态） */
    CANCELLED
}
