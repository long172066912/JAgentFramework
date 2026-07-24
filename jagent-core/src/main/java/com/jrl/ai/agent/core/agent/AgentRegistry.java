package com.jrl.ai.agent.core.agent;

import java.util.Optional;

/**
 * Agent 注册表 — 管理所有 Agent 实例
 */
public interface AgentRegistry {

    void register(Agent agent);

    Optional<Agent> get(String agentId);

    void unregister(String agentId);

    java.util.Collection<Agent> all();
}
