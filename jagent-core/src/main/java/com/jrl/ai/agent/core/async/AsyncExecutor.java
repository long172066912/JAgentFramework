package com.jrl.ai.agent.core.async;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;
import com.jrl.ai.agent.core.task.TaskResult;

import java.util.concurrent.CompletableFuture;

/**
 * 异步执行器 — 支持异步任务执行
 */
public interface AsyncExecutor {

    /**
     * 异步执行任务
     */
    CompletableFuture<TaskResult> executeAsync(Task task, AgentContext context);

    /**
     * 等待所有任务完成
     */
    CompletableFuture<Void> awaitAll(java.util.List<CompletableFuture<TaskResult>> futures);
}
