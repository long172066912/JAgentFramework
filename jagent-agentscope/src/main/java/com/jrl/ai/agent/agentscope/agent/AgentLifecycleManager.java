package com.jrl.ai.agent.agentscope.agent;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentLifecycle;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 生命周期管理器 — Spring 感知的生命周期协调器。
 *
 * <p>实现 {@link SmartLifecycle}，在 Spring 容器启动完成后对所有已注册 Agent
 * 触发 {@link AgentLifecycle#onInit}，在容器关闭时触发 {@link AgentLifecycle#onDestroy}。
 *
 * <p>支持注册多个 {@link AgentLifecycle} 子处理器，广播给所有处理器。
 */
public class AgentLifecycleManager implements AgentLifecycle, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleManager.class);

    private final AgentRegistry agentRegistry;
    private final List<AgentLifecycle> delegates = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 创建生命周期管理器。
     *
     * @param agentRegistry Agent 注册表
     */
    public AgentLifecycleManager(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    /**
     * 注册子生命周期处理器。
     *
     * @param delegate 子处理器
     */
    public void addDelegate(AgentLifecycle delegate) {
        delegates.add(delegate);
    }

    @Override
    public void onInit(Agent agent) {
        for (AgentLifecycle delegate : delegates) {
            try {
                delegate.onInit(agent);
            } catch (Exception e) {
                log.warn("Agent 初始化回调失败: agent={}, delegate={}", agent.id(),
                        delegate.getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    public void onDestroy(Agent agent) {
        for (AgentLifecycle delegate : delegates) {
            try {
                delegate.onDestroy(agent);
            } catch (Exception e) {
                log.warn("Agent 销毁回调失败: agent={}, delegate={}", agent.id(),
                        delegate.getClass().getSimpleName(), e);
            }
        }
    }

    // ===== SmartLifecycle =====

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Agent 生命周期启动，初始化 {} 个 Agent", agentRegistry.all().size());
            for (Agent agent : agentRegistry.all()) {
                onInit(agent);
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Agent 生命周期关闭，销毁 {} 个 Agent", agentRegistry.all().size());
            for (Agent agent : agentRegistry.all()) {
                onDestroy(agent);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // 最后启动，最先停止
    }
}
