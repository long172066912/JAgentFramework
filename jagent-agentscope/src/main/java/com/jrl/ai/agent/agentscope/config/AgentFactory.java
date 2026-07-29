package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.agentscope.model.OpenAICompatibleModel;
import com.jrl.ai.agent.agentscope.skill.SkillToolAdapter;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillRegistry;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
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
    private final SkillRegistry skillRegistry;
    private final ConcurrentHashMap<String, Agent> agentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HarnessAgent> harnessCache = new ConcurrentHashMap<>();

    /**
     * 创建无拦截器的 Agent 工厂。
     *
     * @param properties JAgent 配置属性
     */
    public AgentFactory(JAgentProperties properties) {
        this(properties, List.of(), null);
    }

    /**
     * 创建带拦截器的 Agent 工厂。
     *
     * @param properties   JAgent 配置属性
     * @param interceptors Agent 拦截器列表（会被复制为不可变副本）
     */
    public AgentFactory(JAgentProperties properties, List<AgentInterceptor> interceptors) {
        this(properties, interceptors, null);
    }

    /**
     * 创建带拦截器和 Skill 注册表的 Agent 工厂。
     *
     * @param properties    JAgent 配置属性
     * @param interceptors  Agent 拦截器列表
     * @param skillRegistry Skill 注册表（可选，为 null 时不挂载工具）
     */
    public AgentFactory(JAgentProperties properties, List<AgentInterceptor> interceptors, SkillRegistry skillRegistry) {
        this.properties = properties;
        this.interceptors = interceptors != null ? List.copyOf(interceptors) : List.of();
        this.skillRegistry = skillRegistry;
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

            String agentName = config.getName() != null ? config.getName() : key;
            String agentId = config.getName() != null ? agentName + "(" + key + ")" : key;

            HarnessAgent.Builder builder = HarnessAgent.builder()
                    .agentId(agentId)
                    .name(agentName)
                    .sysPrompt(config.getSysPrompt())
                    .workspace(Path.of(properties.getWorkspace()))
                    .maxIters(config.getMaxIters())
                    .maxRetries(config.getMaxRetries());

            // 按需开启会话持久化 + workspace 上下文 + 记忆工具（单次任务默认关闭，避免 token 累积）
            if (!config.isSessionEnabled()) {
                builder.disableSessionPersistence()
                       .disableWorkspaceContext();
            }
            if (!config.isMemoryEnabled()) {
                builder.disableMemoryTools()
                       .disableMemoryHooks();
            }

            // 如果 YAML 中配置了 API Key，直接构建 Model 对象（避免依赖环境变量）
            Model model = buildModel(config.getModel());
            if (model != null) {
                builder.model(model);
            } else {
                // 回退到字符串引用，由 AgentScope SPI 自动解析（需要环境变量）
                builder.model(config.getModel());
            }

            // 挂载 Skill 工具到 Toolkit
            if (skillRegistry != null && !skillRegistry.all().isEmpty()) {
                Toolkit toolkit = buildToolkit(key, skillRegistry);
                builder.toolkit(toolkit);
            }

            return builder.build();
        });
    }

    /**
     * 根据模型引用构建 Model 对象。
     *
     * <p>模型引用格式为 "provider:model"（如 "dashscope:qwen-plus"、"openai:gpt-4o"）。
     * 支持两种 provider：
     * <ul>
     *   <li>{@code dashscope} — 使用 DashScope 原生 API（可选 baseUrl 用于代理）</li>
     *   <li>{@code openai} — 使用 OpenAI 兼容 API（TokenPay、OneAPI 等）</li>
     * </ul>
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
        Map<String, String> baseUrls = properties.getModel().getBaseUrls();
        String apiKey = apiKeys.get(provider);
        String baseUrl = baseUrls.get(provider);

        if (apiKey == null || apiKey.isBlank()) {
            return null; // 未配置 API Key，回退到环境变量方式
        }

        log.info("构建模型: provider={} model={} baseUrl={}", provider, modelName,
                baseUrl != null ? baseUrl : "(default)");

        return switch (provider.toLowerCase()) {
            case "dashscope" -> DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .stream(true)
                    .baseUrl(baseUrl)
                    .build();
            case "openai" -> new OpenAICompatibleModel(apiKey, modelName, baseUrl, true);
            default -> {
                log.warn("暂不支持自动构建 {} 的 Model，回退到环境变量方式", provider);
                yield null;
            }
        };
    }

    /**
     * 构建 Toolkit 并注册所有 Skill 为 AgentTool。
     *
     * @param agentKey      Agent 标识（用于日志）
     * @param skillRegistry Skill 注册表
     * @return 已注册所有 Skill 的 Toolkit
     */
    private Toolkit buildToolkit(String agentKey, SkillRegistry skillRegistry) {
        Toolkit toolkit = new Toolkit();
        for (Skill skill : skillRegistry.all()) {
            log.info("Agent [{}] 挂载 Skill 工具: {}", agentKey, skill.name());
            toolkit.registerAgentTool(new SkillToolAdapter(skill));
        }
        return toolkit;
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

    /**
     * 渲染指定 Agent 的用户提示词模板。
     *
     * <p>如果 Agent 配置了 {@code user-prompt-template}，则用变量替换占位符后返回；
     * 否则返回 null，业务层需自行构建用户消息。
     *
     * @param agentKey  Agent 标识
     * @param variables 变量映射（key 为占位符名称，如 "contentText"）
     * @return 渲染后的提示词，若无模板则返回 null
     */
    public String renderPrompt(String agentKey, Map<String, Object> variables) {
        JAgentProperties.AgentConfig config = properties.getAgents().get(agentKey);
        if (config == null) {
            throw new IllegalArgumentException("未找到 Agent 配置: " + agentKey);
        }
        return config.renderUserPrompt(variables);
    }

    /**
     * 获取指定 Agent 的配置。
     *
     * @param agentKey Agent 标识
     * @return Agent 配置
     */
    public JAgentProperties.AgentConfig getAgentConfig(String agentKey) {
        return properties.getAgents().get(agentKey);
    }
}
