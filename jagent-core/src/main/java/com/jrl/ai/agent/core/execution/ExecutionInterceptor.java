package com.jrl.ai.agent.core.execution;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;

/**
 * 执行拦截器 — AOP 风格的执行钩子
 */
public interface ExecutionInterceptor {

    /**
     * 执行前拦截
     * @return true 继续执行，false 中止
     */
    default boolean beforeExecute(Task task, AgentContext context) {
        return true;
    }

    /**
     * 执行后拦截
     */
    default void afterExecute(Task task, AgentContext context, com.jrl.ai.agent.core.task.TaskResult result) {
    }

    /**
     * 执行异常拦截
     */
    default void onError(Task task, AgentContext context, Throwable error) {
    }

    /**
     * 拦截器优先级（数值越小优先级越高）
     */
    default int order() {
        return 0;
    }
}
