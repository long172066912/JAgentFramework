package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工厂 — 按配置声明懒创建 HarnessAgent 实例，同时作为 Agent 注册表。
 *
 * <p>使用 ConcurrentHashMap 缓存已创建的 Agent，保证同一标识只创建一次。
 * 实现 {@link AgentRegistry} 接口，支持通过标识查找、手动注册和注销。
 */
public class AgentFactory implements AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final JAgentProperties properties;
    private final List<AgentInterceptor> interceptors;
    private final ConcurrentHashMap<String, Agent> agentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HarnessAgent> harnessCache = new ConcurrentHashMap<>();

    /**
     * 创建无拦截器的 Agent 工厂。
     *
     * @param properties JAgent 配置属性
     */
    public AgentFactory(JAgentProperties properties) {
        this(properties, List.of());
    }

    /**
     * 创建带拦截器的 Agent 工厂。
     *
     * @param properties   JAgent 配置属性
     * @param interceptors Agent 拦截器列表（会被复制为不可变副本）
     */
    public AgentFactory(JAgentProperties properties, List<AgentInterceptor> interceptors) {
        this.properties = properties;
        this.interceptors = interceptors != null ? List.copyOf(interceptors) : List.of();
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
            return new AgentScopeAgentAdapter(harness, interceptors);
        });
    }

    @Override
    public void register(Agent agent) {
        agentCache.put(agent.id(), agent);
    }

    @Override
    public Optional<Agent> get(String agentId) {
        return Optional.ofNullable(agentCache.get(agentId));
    }

    @Override
    public void unregister(String agentId) {
        agentCache.remove(agentId);
        harnessCache.remove(agentId);
    }

    @Override
    public Collection<Agent> all() {
        return allAgents();
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

            HarnessAgent.Builder builder = HarnessAgent.builder()
                    .agentId(key)
                    .name(config.getName() != null ? config.getName() : key)
                    .sysPrompt(config.getSysPrompt())
                    .workspace(Path.of(properties.getWorkspace()))
                    .maxIters(config.getMaxIters())
                    .maxRetries(config.getMaxRetries());

            // 如果 YAML 中配置了 API Key，直接构建 Model 对象（避免依赖环境变量）
            Model model = buildModel(config.getModel());
            if (model != null) {
                builder.model(model);
            } else {
                // 回退到字符串引用，由 AgentScope SPI 自动解析（需要环境变量）
                builder.model(config.getModel());
            }

            return builder.build();
        });
    }

    /**
     * 根据模型引用构建 Model 对象。
     *
     * <p>模型引用格式为 "provider:model"（如 "dashscope:qwen-plus"）。
     * 如果 YAML 中配置了对应 provider 的 API Key，则直接构建 Model；
     * 否则返回 null，由 AgentScope SPI 自动解析。
     *
     * @param modelRef 模型引用（格式: "provider:model"）
     * @return Model 对象，或 null（未配置 API Key 时）
     */
    private Model buildModel(String modelRef) {
        int colonIdx = modelRef.indexOf(':');
        if (colonIdx <= 0) return null;

        String provider = modelRef.substring(0, colonIdx);
        String modelName = modelRef.substring(colonIdx + 1);
        Map<String, String> apiKeys = properties.getModel().getApiKeys();
        String apiKey = apiKeys.get(provider);
        if (apiKey == null || apiKey.isBlank()) {
            return null; // 未配置 API Key，回退到环境变量方式
        }

        log.info("使用 YAML 配置的 API Key 构建模型: provider={} model={}", provider, modelName);
        return switch (provider.toLowerCase()) {
            case "dashscope" -> DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .stream(true)
                    .build();
            default -> {
                log.warn("暂不支持自动构建 {} 的 Model，回退到环境变量方式", provider);
                yield null;
            }
        };
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
