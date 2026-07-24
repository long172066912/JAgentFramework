package com.jrl.ai.agent.core.execution;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;
import com.jrl.ai.agent.core.task.TaskResult;

/**
 * 执行引擎 — 编排 Agent 的执行流程
 */
public interface ExecutionEngine {

    /**
     * 执行任务
     */
    TaskResult execute(Task task, AgentContext context);

    /**
     * 中断执行
     */
    void cancel(String taskId);
}
