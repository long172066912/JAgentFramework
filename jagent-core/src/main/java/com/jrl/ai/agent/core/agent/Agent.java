package com.jrl.ai.agent.core.agent;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;

/**
 * Agent — 框架的核心抽象，定义智能体的基本行为契约。
 *
 * <p>每个 Agent 拥有唯一标识和名称，能够接收 {@link ChatMessage} 输入并在
 * {@link AgentContext} 上下文中执行任务，返回 {@link TaskResult}。
 * 具体执行逻辑由适配层（如 AgentScope）实现。
 *
 * @see AgentLifecycle
 * @see AgentRegistry
 */
public interface Agent {

    /**
     * 获取 Agent 的全局唯一标识。
     *
     * @return 不可为空的唯一 ID
     */
    String id();

    /**
     * 获取 Agent 的可读名称，用于日志、监控和路由展示。
     *
     * @return Agent 名称
     */
    String name();

    /**
     * 同步执行 Agent。
     *
     * @param input   用户输入消息
     * @param context 运行时上下文（包含会话、用户、扩展属性等）
     * @return 任务执行结果
     */
    TaskResult execute(ChatMessage input, AgentContext context);
}
