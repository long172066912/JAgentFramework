package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.core.agent.Agent;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工厂 — 按配置声明懒创建 HarnessAgent 实例。
 *
 * <p>使用 ConcurrentHashMap 缓存已创建的 Agent，保证同一标识只创建一次。
 * 同时维护 jagent Agent 适配器的注册表，支持通过标识查找。
 */
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final JAgentProperties properties;
    private final ConcurrentHashMap<String, Agent> agentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HarnessAgent> harnessCache = new ConcurrentHashMap<>();

    public AgentFactory(JAgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 按标识获取 Agent（jagent 接口）。
     *
     * @param agentKey Agent 配置标识
     * @return jagent Agent 适配器
     */
    public Agent getAgent(String agentKey) {
        return agentCache.computeIfAbsent(agentKey, key -> {
            HarnessAgent harness = getHarnessAgent(key);
            return new AgentScopeAgentAdapter(harness);
        });
    }

    /**
     * 按标识获取 HarnessAgent（AgentScope 原生）。
     *
     * @param agentKey Agent 配置标识
     * @return HarnessAgent 实例
     */
    public HarnessAgent getHarnessAgent(String agentKey) {
        return harnessCache.computeIfAbsent(agentKey, key -> {
            JAgentProperties.AgentConfig config = properties.getAgents().get(key);
            if (config == null) {
                throw new IllegalArgumentException("未找到 Agent 配置: " + key);
            }
            log.info("创建 Agent [{}] model={} workspace={}", key, config.getModel(), properties.getWorkspace());
            return HarnessAgent.builder()
                    .agentId(key)
                    .name(config.getName() != null ? config.getName() : key)
                    .sysPrompt(config.getSysPrompt())
                    .model(config.getModel())
                    .workspace(Path.of(properties.getWorkspace()))
                    .maxIters(config.getMaxIters())
                    .maxRetries(config.getMaxRetries())
                    .build();
        });
    }

    /**
     * 获取所有已注册的 Agent。
     *
     * @return Agent 集合
     */
    public Collection<Agent> allAgents() {
        // 确保所有配置的 Agent 都已创建
        properties.getAgents().keySet().forEach(this::getAgent);
        return List.copyOf(agentCache.values());
    }

    /**
     * 查找指定标识的 Agent。
     *
     * @param agentKey Agent 标识
     * @return Agent（如果存在）
     */
    public Optional<Agent> findAgent(String agentKey) {
        if (!properties.getAgents().containsKey(agentKey)) return Optional.empty();
        return Optional.of(getAgent(agentKey));
    }

    /**
     * 获取所有已注册的 Agent 标识。
     *
     * @return Agent 标识集合
     */
    public Collection<String> allAgentKeys() {
        return properties.getAgents().keySet();
    }
}
