package com.jrl.ai.agent.core.router;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;

/**
 * 路由器 — 根据任务/上下文决定由哪个 Agent 处理
 */
public interface Router {

    /**
     * 路由到目标 Agent
     */
    Agent route(Task task, AgentContext context);

    /**
     * 路由器名称
     */
    String name();
}
