package com.jrl.ai.agent.core.agent;

/**
 * Agent 生命周期回调
 */
public interface AgentLifecycle {

    /**
     * Agent 初始化时调用
     */
    void onInit(Agent agent);

    /**
     * Agent 销毁时调用
     */
    void onDestroy(Agent agent);
}
