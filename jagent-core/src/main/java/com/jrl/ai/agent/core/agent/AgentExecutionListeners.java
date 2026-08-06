package com.jrl.ai.agent.core.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Agent 执行监听器通知工具 — 安全地向监听器分发执行事件。
 *
 * <p>推荐入口为基于 {@link AgentExecutionListenerRegistry} 的重载：
 * 注册表按 agentKey 查表得到目标监听器，无需全量扫描。
 * 分发前仍会调用 {@link AgentExecutionListener#supports} 二次过滤，
 * 支持监听器自定义更细粒度的订阅条件。
 * 单个监听器抛出的异常只记录日志，不会中断其他监听器或 Agent 主执行流程。
 */
public final class AgentExecutionListeners {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionListeners.class);

    private AgentExecutionListeners() {
        // 工具类禁止实例化
    }

    /**
     * 通知注册表中匹配的执行开始监听器。
     *
     * @param registry 监听器注册表（null 或空时直接返回）
     * @param event    执行开始事件
     */
    public static void notifyStart(AgentExecutionListenerRegistry registry, AgentExecutionEvent event) {
        if (registry == null || registry.isEmpty()) {
            return;
        }
        notifyStart(registry.listenersFor(event.agentKey()), event);
    }

    /**
     * 通知注册表中匹配的执行结束监听器。
     *
     * @param registry 监听器注册表（null 或空时直接返回）
     * @param event    执行结束事件
     */
    public static void notifyEnd(AgentExecutionListenerRegistry registry, AgentExecutionEvent event) {
        if (registry == null || registry.isEmpty()) {
            return;
        }
        notifyEnd(registry.listenersFor(event.agentKey()), event);
    }

    /**
     * 通知所有监听器执行开始。
     *
     * @param listeners 监听器列表（null 或空列表时直接返回）
     * @param event     执行开始事件
     */
    public static void notifyStart(List<AgentExecutionListener> listeners, AgentExecutionEvent event) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (AgentExecutionListener listener : listeners) {
            if (!supports(listener, event)) {
                continue;
            }
            try {
                listener.onExecutionStart(event);
            } catch (Exception e) {
                log.warn("[AgentExecutionListener] onExecutionStart 异常: listener={}, agent={}",
                        listener.getClass().getSimpleName(), event.agentId(), e);
            }
        }
    }

    /**
     * 通知所有监听器执行结束。
     *
     * @param listeners 监听器列表（null 或空列表时直接返回）
     * @param event     执行结束事件
     */
    public static void notifyEnd(List<AgentExecutionListener> listeners, AgentExecutionEvent event) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (AgentExecutionListener listener : listeners) {
            if (!supports(listener, event)) {
                continue;
            }
            try {
                listener.onExecutionEnd(event);
            } catch (Exception e) {
                log.warn("[AgentExecutionListener] onExecutionEnd 异常: listener={}, agent={}",
                        listener.getClass().getSimpleName(), event.agentId(), e);
            }
        }
    }

    /**
     * 安全调用监听器的 supports 判断（异常时视为不接收，避免影响主流程）。
     */
    private static boolean supports(AgentExecutionListener listener, AgentExecutionEvent event) {
        try {
            return listener.supports(event);
        } catch (Exception e) {
            log.warn("[AgentExecutionListener] supports 异常: listener={}, agent={}",
                    listener.getClass().getSimpleName(), event.agentId(), e);
            return false;
        }
    }
}
