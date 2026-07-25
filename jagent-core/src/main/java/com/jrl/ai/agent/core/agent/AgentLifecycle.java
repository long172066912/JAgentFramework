package com.jrl.ai.agent.core.agent;

/**
 * Agent 生命周期回调接口。
 *
 * <p>允许在 Agent 初始化和销毁时执行自定义逻辑，
 * 如资源分配、连接建立、状态清理等。
 *
 * @see Agent
 */
public interface AgentLifecycle {

    /**
     * Agent 初始化时调用。
     *
     * <p>在 Agent 注册完成后触发，可在此执行资源初始化、
     * 依赖注入校验等准备工作。
     *
     * @param agent 已完成初始化的 Agent 实例
     */
    void onInit(Agent agent);

    /**
     * Agent 销毁时调用。
     *
     * <p>在 Agent 从注册表注销时触发，可在此执行资源释放、
     * 连接关闭、状态持久化等清理工作。
     *
     * @param agent 即将销毁的 Agent 实例
     */
    void onDestroy(Agent agent);
}
