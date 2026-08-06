package com.jrl.ai.agent.core.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 执行监听器注册表 — 注册时即按 agentKey 分桶，分发时按 key 直接查表。
 *
 * <p>内部结构为 {@code Map<agentKey, List<Listener>>} + 全局监听器列表：
 * <ul>
 *   <li>作用域监听器（{@link ScopedAgentExecutionListener}）按其绑定的 key 入桶，
 *       分发时只接收绑定 Agent 的事件</li>
 *   <li>其他监听器注册为全局监听器，接收所有 Agent 的事件</li>
 * </ul>
 *
 * <p>相比逐个遍历 + supports 过滤，注册表在分发时只需一次 Map 查找，
 * 监听器数量多时避免全量扫描。支持运行期动态注册（线程安全）。
 *
 * @see AgentExecutionListener
 * @see ScopedAgentExecutionListener
 */
public class AgentExecutionListenerRegistry {

    /** agentKey → 绑定该 key 的监听器列表 */
    private final Map<String, List<AgentExecutionListener>> scopedListeners = new ConcurrentHashMap<>();

    /** 全局监听器列表（接收所有事件） */
    private final List<AgentExecutionListener> globalListeners = new CopyOnWriteArrayList<>();

    /**
     * 自动注册 — 作用域监听器按绑定 key 入桶，其他注册为全局监听器。
     *
     * @param listener 监听器实例（null 忽略）
     */
    public void register(AgentExecutionListener listener) {
        if (listener == null) {
            return;
        }
        if (listener instanceof ScopedAgentExecutionListener scoped && !scoped.agentKeys().isEmpty()) {
            for (String agentKey : scoped.agentKeys()) {
                register(agentKey, listener);
            }
        } else {
            globalListeners.add(listener);
        }
    }

    /**
     * 将监听器绑定到指定 agentKey。
     *
     * @param agentKey Agent 配置键名（如 {@code chat}）
     * @param listener 监听器实例（任一参数为 null 时忽略）
     */
    public void register(String agentKey, AgentExecutionListener listener) {
        if (agentKey == null || listener == null) {
            return;
        }
        scopedListeners.computeIfAbsent(agentKey, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 注册全局监听器（接收所有 Agent 的事件）。
     *
     * @param listener 监听器实例（null 忽略）
     */
    public void registerGlobal(AgentExecutionListener listener) {
        if (listener != null) {
            globalListeners.add(listener);
        }
    }

    /**
     * 查找指定 agentKey 对应的监听器列表（全局监听器 + 绑定该 key 的监听器）。
     *
     * <p>agentKey 为 null 时只返回全局监听器。
     *
     * @param agentKey Agent 配置键名（可为 null）
     * @return 应接收该 Agent 事件的监听器列表
     */
    public List<AgentExecutionListener> listenersFor(String agentKey) {
        List<AgentExecutionListener> scoped = agentKey != null ? scopedListeners.get(agentKey) : null;
        if (scoped == null || scoped.isEmpty()) {
            return List.copyOf(globalListeners);
        }
        List<AgentExecutionListener> result = new ArrayList<>(globalListeners.size() + scoped.size());
        result.addAll(globalListeners);
        result.addAll(scoped);
        return result;
    }

    /**
     * 注册表是否为空。
     *
     * @return true 表示无任何监听器
     */
    public boolean isEmpty() {
        return globalListeners.isEmpty() && scopedListeners.isEmpty();
    }
}
