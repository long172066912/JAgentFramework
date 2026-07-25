package com.jrl.ai.agent.core.agent;

import java.util.Optional;

/**
 * Agent 注册表 — 管理所有 Agent 实例的注册、查找与注销。
 *
 * <p>提供基于 ID 的 Agent 访问能力，是 {@link com.jrl.ai.agent.core.router.Router}
 * 进行任务路由的基础。
 *
 * @see Agent
 */
public interface AgentRegistry {

    /**
     * 注册一个 Agent。
     *
     * @param agent 待注册的 Agent 实例
     */
    void register(Agent agent);

    /**
     * 根据 ID 查找 Agent。
     *
     * @param agentId Agent 唯一标识
     * @return 匹配的 Agent，不存在时返回 {@link Optional#empty()}
     */
    Optional<Agent> get(String agentId);

    /**
     * 注销指定 ID 的 Agent。
     *
     * @param agentId 待注销的 Agent 唯一标识
     */
    void unregister(String agentId);

    /**
     * 获取所有已注册的 Agent。
     *
     * @return 不可变的 Agent 集合
     */
    java.util.Collection<Agent> all();
}
