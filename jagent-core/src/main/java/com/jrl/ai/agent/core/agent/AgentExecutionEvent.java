package com.jrl.ai.agent.core.agent;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;

/**
 * Agent 执行事件 — 描述一次 Agent 执行的快照，传递给 {@link AgentExecutionListener}。
 *
 * <p>覆盖同步、流式、异步三种执行通道：
 * <ul>
 *   <li>{@code SYNC} — 同步执行，结束时携带 {@link TaskResult}</li>
 *   <li>{@code STREAM} — 流式执行，结束时 result 为 null，通过 error 判断成败</li>
 *   <li>{@code ASYNC} — 异步任务执行，结束时 result 为 null，通过 error 判断成败</li>
 * </ul>
 *
 * @param agentId      Agent 标识
 * @param agentName    Agent 名称
 * @param agentKey     Agent 配置键名（如 {@code chat}），可能为 null（非工厂创建的 Agent）
 * @param input        用户输入消息（异步恢复执行时为确认消息）
 * @param context      运行时上下文
 * @param mode         执行模式
 * @param startTimeMs  执行开始时间戳（毫秒）
 * @param durationMs   执行耗时（毫秒），执行开始事件为 null
 * @param result       同步执行结果（流式/异步为 null）
 * @param error        执行异常（成功时为 null）
 */
public record AgentExecutionEvent(
        String agentId,
        String agentName,
        String agentKey,
        ChatMessage input,
        AgentContext context,
        ExecutionMode mode,
        long startTimeMs,
        Long durationMs,
        TaskResult result,
        Throwable error
) {

    /**
     * Agent 执行模式。
     */
    public enum ExecutionMode {
        /** 同步执行 */
        SYNC,
        /** 流式执行 */
        STREAM,
        /** 异步任务执行 */
        ASYNC
    }

    /**
     * 构建执行开始事件（agentKey 为 null）。
     *
     * @param agent   即将执行的 Agent
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @param mode    执行模式
     * @return 执行开始事件
     */
    public static AgentExecutionEvent start(Agent agent, ChatMessage input,
                                            AgentContext context, ExecutionMode mode) {
        return start(agent, null, input, context, mode);
    }

    /**
     * 构建执行开始事件。
     *
     * @param agent    即将执行的 Agent
     * @param agentKey Agent 配置键名（可为 null）
     * @param input    用户输入消息
     * @param context  运行时上下文
     * @param mode     执行模式
     * @return 执行开始事件
     */
    public static AgentExecutionEvent start(Agent agent, String agentKey, ChatMessage input,
                                            AgentContext context, ExecutionMode mode) {
        return new AgentExecutionEvent(agent.id(), agent.name(), agentKey, input, context, mode,
                System.currentTimeMillis(), null, null, null);
    }

    /**
     * 构建执行结束事件（agentKey 为 null）。
     *
     * @param agent       已执行完成的 Agent
     * @param input       用户输入消息
     * @param context     运行时上下文
     * @param mode        执行模式
     * @param startTimeMs 执行开始时间戳（毫秒）
     * @param result      同步执行结果（流式/异步传 null）
     * @param error       执行异常（成功时传 null）
     * @return 执行结束事件
     */
    public static AgentExecutionEvent end(Agent agent, ChatMessage input, AgentContext context,
                                          ExecutionMode mode, long startTimeMs,
                                          TaskResult result, Throwable error) {
        return end(agent, null, input, context, mode, startTimeMs, result, error);
    }

    /**
     * 构建执行结束事件。
     *
     * @param agent       已执行完成的 Agent
     * @param agentKey    Agent 配置键名（可为 null）
     * @param input       用户输入消息
     * @param context     运行时上下文
     * @param mode        执行模式
     * @param startTimeMs 执行开始时间戳（毫秒）
     * @param result      同步执行结果（流式/异步传 null）
     * @param error       执行异常（成功时传 null）
     * @return 执行结束事件
     */
    public static AgentExecutionEvent end(Agent agent, String agentKey, ChatMessage input, AgentContext context,
                                          ExecutionMode mode, long startTimeMs,
                                          TaskResult result, Throwable error) {
        return new AgentExecutionEvent(agent.id(), agent.name(), agentKey, input, context, mode,
                startTimeMs, System.currentTimeMillis() - startTimeMs, result, error);
    }

    /**
     * 判断事件是否来自指定配置键名的 Agent。
     *
     * @param key Agent 配置键名（如 {@code chat}）
     * @return true 表示匹配
     */
    public boolean matchesAgentKey(String key) {
        return key != null && key.equals(agentKey);
    }

    /**
     * 执行是否成功（无异常，且结果不为失败状态）。
     *
     * @return true 表示执行成功
     */
    public boolean isSuccess() {
        return error == null && (result == null || result.isSuccess());
    }
}
