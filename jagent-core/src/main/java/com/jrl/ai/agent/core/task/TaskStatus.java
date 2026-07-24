package com.jrl.ai.agent.core.task;

/**
 * 任务状态
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    WAITING_INPUT,
    COMPLETED,
    FAILED,
    CANCELLED
}
