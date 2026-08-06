package com.jrl.ai.agent.core.agent;

/**
 * Agent 执行监听器 — 感知所有 Agent 执行的开始与结束事件。
 *
 * <p>与 {@link AgentInterceptor} 的区别：
 * <ul>
 *   <li>拦截器只作用于同步执行链路，且可影响执行流程（环绕短路）</li>
 *   <li>监听器覆盖同步、流式、异步三种执行通道，纯观察不干预执行</li>
 * </ul>
 *
 * <p>典型用途：执行日志记录、耗时统计、外部事件推送、审计追踪等。
 * 所有方法均提供默认空实现，实现方按需覆写。
 *
 * <p>实现方抛出的异常不会中断 Agent 执行流程（由框架统一捕获并记录日志）。
 *
 * @see AgentExecutionEvent
 */
public interface AgentExecutionListener {

    /**
     * 判断是否接收指定事件 — 框架分发前调用，不匹配的事件不会传递给本监听器。
     *
     * <p>默认返回 true（接收所有事件）。只关注特定 Agent 时可覆写，
     * 或直接继承 {@link ScopedAgentExecutionListener} 按配置键名绑定。
     *
     * @param event 执行事件
     * @return true 表示接收该事件
     */
    default boolean supports(AgentExecutionEvent event) {
        return true;
    }

    /**
     * Agent 执行开始时调用（仅当 {@link #supports} 返回 true 时）。
     *
     * @param event 执行开始事件（含 Agent 标识、输入、上下文、执行模式）
     */
    default void onExecutionStart(AgentExecutionEvent event) {}

    /**
     * Agent 执行结束时调用（无论成功或失败，仅当 {@link #supports} 返回 true 时）。
     *
     * <p>通过 {@link AgentExecutionEvent#isSuccess()} 判断成败，
     * 通过 {@link AgentExecutionEvent#error()} 获取失败原因。
     *
     * @param event 执行结束事件（含耗时、同步结果、异常信息）
     */
    default void onExecutionEnd(AgentExecutionEvent event) {}
}
