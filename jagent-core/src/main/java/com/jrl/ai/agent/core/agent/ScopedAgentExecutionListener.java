package com.jrl.ai.agent.core.agent;

import java.util.Set;

/**
 * 按 Agent 配置键名绑定的执行监听器基类 — 只接收指定 Agent 的执行事件。
 *
 * <p>框架在分发事件前先调用 {@link #supports} 过滤，不匹配的事件不会
 * 到达 {@link #onExecutionStart} / {@link #onExecutionEnd}，
 * 实现方无需在回调内重复判断。
 *
 * <p>用法示例：
 * <pre>{@code
 * public class ChatLogListener extends ScopedAgentExecutionListener {
 *     public ChatLogListener() { super("chat"); }
 *
 *     @Override
 *     public void onExecutionStart(AgentExecutionEvent event) { ... }
 *
 *     @Override
 *     public void onExecutionEnd(AgentExecutionEvent event) { ... }
 * }
 * }</pre>
 *
 * <p>注意：事件不携带 agentKey 时（非工厂创建的 Agent 直接执行），默认不接收。
 */
public abstract class ScopedAgentExecutionListener implements AgentExecutionListener {

    /** 绑定的 Agent 配置键名集合 */
    private final Set<String> agentKeys;

    /**
     * 绑定一个或多个 Agent 配置键名。
     *
     * @param agentKeys Agent 配置键名（application.yml 中 jagent.agents 下的 key）
     */
    protected ScopedAgentExecutionListener(String... agentKeys) {
        this.agentKeys = Set.of(agentKeys);
    }

    /**
     * 仅接收绑定键名对应 Agent 的事件。
     *
     * @param event 执行事件
     * @return true 表示接收该事件
     */
    @Override
    public boolean supports(AgentExecutionEvent event) {
        return event.agentKey() != null && agentKeys.contains(event.agentKey());
    }

    /**
     * 获取绑定的 Agent 配置键名集合。
     *
     * @return 键名集合（不可变）
     */
    public Set<String> agentKeys() {
        return agentKeys;
    }
}
