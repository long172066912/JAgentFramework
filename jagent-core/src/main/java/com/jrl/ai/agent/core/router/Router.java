package com.jrl.ai.agent.core.router;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;

/**
 * 路由器 — 根据任务/上下文决定由哪个 Agent 处理。
 *
 * <p>在多 Agent 场景下，路由器负责将 {@link Task} 分发到
 * 最合适的 {@link Agent}。路由策略可由实现方自定义
 * （如基于任务类型、Agent 能力、负载均衡等）。
 *
 * @see Agent
 * @see AgentRegistry
 */
public interface Router {

    /**
     * 将任务路由到目标 Agent。
     *
     * @param task    待处理的任务
     * @param context 运行时上下文
     * @return 被选中的 Agent
     */
    Agent route(Task task, AgentContext context);

    /**
     * 获取路由器名称。
     *
     * @return 路由器名称
     */
    String name();
}
