package com.jrl.ai.agent.core.agent;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;

/**
 * Agent 执行拦截器 — 在 Agent 执行的前置、后置、环绕阶段插入自定义逻辑。
 *
 * <p>典型用途：监控指标采集、日志追踪、权限校验、限流、超时控制等。
 * 所有方法均提供默认空实现，实现方按需覆写。
 *
 * <p>环绕方法 {@link #aroundExecute} 可控制是否继续执行下游链路：
 * <ul>
 *   <li>调用 {@code chain.proceed()} 继续执行</li>
 *   <li>不调用则短路返回自定义结果</li>
 * </ul>
 *
 * @see Agent
 */
public interface AgentInterceptor {

    /**
     * Agent 执行前调用（前置通知）。
     *
     * @param agent   即将执行的 Agent
     * @param input   用户输入消息
     * @param context 运行时上下文
     */
    default void beforeExecute(Agent agent, ChatMessage input, AgentContext context) {}

    /**
     * Agent 执行成功后调用（后置通知）。
     *
     * @param agent   已执行完成的 Agent
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @param result  执行结果
     */
    default void afterExecute(Agent agent, ChatMessage input, AgentContext context, TaskResult result) {}

    /**
     * Agent 执行异常时调用（异常通知）。
     *
     * @param agent   执行失败的 Agent
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @param error   异常信息
     */
    default void onError(Agent agent, ChatMessage input, AgentContext context, Throwable error) {}

    /**
     * Agent 执行环绕通知 — 可控制是否继续执行下游链路。
     *
     * <p>默认实现直接调用 {@code chain.proceed()}。
     *
     * @param agent   目标 Agent
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @param chain   执行链，调用 {@code proceed()} 继续执行
     * @return 执行结果
     */
    default TaskResult aroundExecute(Agent agent, ChatMessage input, AgentContext context, ExecutionChain chain) {
        return chain.proceed(input, context);
    }

    /**
     * 执行链抽象 — 代表拦截器链中的下一个环节。
     */
    @FunctionalInterface
    interface ExecutionChain {
        /**
         * 继续执行下游链路。
         *
         * @param input   用户输入消息
         * @param context 运行时上下文
         * @return 执行结果
         */
        TaskResult proceed(ChatMessage input, AgentContext context);
    }
}
