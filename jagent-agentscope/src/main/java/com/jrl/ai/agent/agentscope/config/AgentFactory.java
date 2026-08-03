package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.agentscope.middleware.SubagentTraceMiddleware;
import com.jrl.ai.agent.agentscope.model.OpenAICompatibleModel;
import com.jrl.ai.agent.agentscope.skill.SkillToolAdapter;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillRegistry;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
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
    private final ConcurrentHashMap<String, AgentStateStore> sharedStateStores = new ConcurrentHashMap<>();

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
        // 启动阶段：将父 Agent 的 maxContextTokens 传播到未配置的子 Agent
        propagateMaxContextTokens();
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

            String sysPrompt = config.getSysPrompt();
            List<JAgentProperties.SubagentRef> subagentRefs = config.getSubagents();
            log.info("Agent [{}] subagents 配置: {}", key, subagentRefs != null ? subagentRefs.size() : 0);

            // 自动注入子 Agent 调度规则到 sys-prompt
            if (subagentRefs != null && !subagentRefs.isEmpty()) {
                sysPrompt = injectSubagentRules(sysPrompt, subagentRefs);
            }

            HarnessAgent.Builder builder = HarnessAgent.builder()
                    .agentId(agentId)
                    .name(agentName)
                    .sysPrompt(sysPrompt)
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

            // 按需开启 Plan Mode 规划模式（复杂任务先规划再执行）
            if (config.isPlanModeEnabled()) {
                builder.enablePlanMode();
                log.info("Agent [{}] 已启用 Plan Mode 规划模式", key);
            }

            // 按需开启 OpenTelemetry 链路追踪（可视化执行链路）
            if (config.isTracingEnabled()) {
                builder.middleware(new OtelTracingMiddleware());
                log.info("Agent [{}] 已启用 OpenTelemetry 链路追踪", key);
            }

            // 配置上下文压缩与工具结果驱逐（长对话防溢出）
            int maxTokens = config.getMaxContextTokens();
            if (maxTokens > 0) {
                builder.maxContextTokens(maxTokens);
                builder.compaction(CompactionConfig.builder().build());
                builder.toolResultEviction(ToolResultEvictionConfig.defaults());
                log.info("Agent [{}] 已启用上下文压缩，maxContextTokens={}", key, maxTokens);
            }

            // 配置权限引擎（用户确认机制）
            configurePermission(builder, config, key);

            // 如果 YAML 中配置了 API Key，直接构建 Model 对象（避免依赖环境变量）
            Model model = buildModel(config.getModel(), config.isEnableSearch(), config.isEnableThinking());
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

            // 共享会话组：同组 Agent 共享同一个 AgentStateStore，实现多 Agent 协同感知上下文
            String sessionGroup = config.getSessionGroup();
            if (sessionGroup != null && !sessionGroup.isBlank()) {
                AgentStateStore sharedStore = sharedStateStores.computeIfAbsent(sessionGroup,
                        group -> new JsonFileAgentStateStore(
                                Path.of(properties.getWorkspace(), "shared-sessions", group)));
                builder.stateStore(sharedStore);
                log.info("Agent [{}] 加入共享会话组: {}", key, sessionGroup);
            }

            // Markdown Skill 热加载：配置目录后自动扫描 SKILL.md，修改即生效无需重启
            String skillsDir = config.getSkillsDir();
            if (skillsDir != null && !skillsDir.isBlank()) {
                Path skillsPath = Path.of(properties.getWorkspace(), skillsDir);
                if (java.nio.file.Files.isDirectory(skillsPath)) {
                    builder.skillRepository(new FileSystemSkillRepository(skillsPath));
                    log.info("Agent [{}] 加载 Markdown Skill 目录: {}", key, skillsPath);
                } else {
                    log.warn("Agent [{}] Markdown Skill 目录不存在: {}", key, skillsPath);
                }
            }

            // 子 Agent 编排：引用已配置的 Agent，框架自动查找其配置
            if (subagentRefs != null && !subagentRefs.isEmpty()) {
                // 注册模型解析器，让子 Agent 能通过字符串引用找到 Model 对象
                builder.modelResolver(modelRef -> {
                    if (modelRef == null || modelRef.isBlank()) return null;
                    return buildModel(modelRef, false, false);
                });

                for (JAgentProperties.SubagentRef subRef : subagentRefs) {
                    String subName = subRef.getId();
                    JAgentProperties.AgentConfig subConfig = properties.getAgents().get(subName);
                    if (subConfig == null) {
                        log.warn("Agent [{}] 引用的子 Agent [{}] 未配置，跳过", key, subName);
                        continue;
                    }
                    String subAgentName = subConfig.getName() != null ? subConfig.getName() : subName;
                    // 优先使用配置的 description，否则从 sys-prompt 提取
                    String subDesc = subConfig.getDescription() != null && !subConfig.getDescription().isBlank()
                            ? subConfig.getDescription()
                            : extractDescription(subConfig.getSysPrompt());
                    String subagentPrompt = subConfig.getSysPrompt();
                    SubagentDeclaration.Builder declBuilder = SubagentDeclaration.builder()
                            .name(subName)
                            .description(subAgentName + ": " + subDesc)
                            .inlineAgentsBody(subagentPrompt);
                    // 设置子 Agent 的模型（通过 modelResolver 解析）
                    if (subConfig.getModel() != null) {
                        declBuilder.model(subConfig.getModel());
                    }
                    builder.subagent(declBuilder.build());
                }
                // 注册子 Agent 调度日志增强中间件
                builder.middleware(new SubagentTraceMiddleware());
                log.info("Agent [{}] 注册 {} 个子 Agent", key, subagentRefs.size());
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
     *   <li>{@code dashscope} — 使用 DashScope 原生 API（可选 baseUrl 用于代理，可选 enableSearch 联网搜索）</li>
     *   <li>{@code openai} — 使用 OpenAI 兼容 API（TokenPay、OneAPI 等）</li>
     * </ul>
     * 如果 YAML 中配置了对应 provider 的 API Key，则直接构建 Model；
     * 否则返回 null，由 AgentScope SPI 自动解析。
     *
     * @param modelRef       模型引用（格式: "provider:model"）
     * @param enableSearch   是否启用联网搜索（仅 dashscope 支持）
     * @param enableThinking 是否启用推理/思考模式
     * @return Model 对象，或 null（未配置 API Key 时）
     */
    private Model buildModel(String modelRef, boolean enableSearch, boolean enableThinking) {
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
            case "dashscope" -> {
                var b = DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .stream(true)
                        .baseUrl(baseUrl);
                if (enableSearch) {
                    b.enableSearch(true);
                    log.info("DashScope 联网搜索已启用: model={}", modelName);
                }
                yield b.build();
            }
            case "openai" -> new OpenAICompatibleModel(apiKey, modelName, baseUrl, false, enableThinking);
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

    /**
     * 从系统提示词中提取简短描述（取第一行非空内容，截断到 50 字符）。
     *
     * @param sysPrompt 系统提示词
     * @return 简短描述
     */
    private String extractDescription(String sysPrompt) {
        if (sysPrompt == null || sysPrompt.isBlank()) {
            return "A specialized assistant";
        }
        String firstLine = sysPrompt.lines()
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("A specialized assistant");
        return firstLine.length() > 50 ? firstLine.substring(0, 47) + "..." : firstLine;
    }

    /**
     * 自动生成并注入子 Agent 调度规则到父 Agent 的 sys-prompt。
     *
     * <p>根据子 Agent 的 description 和 keywords（来自 SubagentRef）生成调度规则，
     * 告诉父 Agent 在什么情况下应该调度哪个子 Agent。
     *
     * @param baseSysPrompt  原始 sys-prompt
     * @param subagentRefs   子 Agent 引用列表（含 keywords）
     * @return 注入调度规则后的 sys-prompt
     */
    private String injectSubagentRules(String baseSysPrompt, List<JAgentProperties.SubagentRef> subagentRefs) {
        StringBuilder rules = new StringBuilder();
        rules.append("\n\n## 子 Agent 调度规则（必须遵守）\n");
        rules.append("你可以调度以下子 Agent 处理专业任务，不要自己完成这些任务：\n");

        for (JAgentProperties.SubagentRef subRef : subagentRefs) {
            String subName = subRef.getId();
            JAgentProperties.AgentConfig subConfig = properties.getAgents().get(subName);
            if (subConfig == null) continue;

            String subAgentName = subConfig.getName() != null ? subConfig.getName() : subName;
            String desc = subConfig.getDescription() != null && !subConfig.getDescription().isBlank()
                    ? subConfig.getDescription()
                    : extractDescription(subConfig.getSysPrompt());
            // keywords 来自 SubagentRef（父 Agent 视角），不是子 Agent 自身配置
            List<String> keywords = subRef.getKeywords();

            rules.append("\n### ").append(subAgentName).append(" (agent_id=\"").append(subName).append("\")\n");
            rules.append("- 能力：").append(desc).append("\n");

            if (keywords != null && !keywords.isEmpty()) {
                rules.append("- 触发条件：用户输入包含以下关键词时调度 → ");
                rules.append(String.join("、", keywords)).append("\n");
            }

            rules.append("- 调用方式：agent_spawn(agent_id=\"").append(subName)
                    .append("\", task=\"具体任务描述\")\n");
        }

        rules.append("\n调度原则：\n");
        rules.append("1. 识别用户意图，匹配到子 Agent 能力范围时必须调度\n");
        rules.append("2. 调度后将子 Agent 的返回结果直接呈现给用户\n");
        rules.append("3. 不要自己完成子 Agent 能处理的任务\n");

        log.debug("自动注入子 Agent 调度规则: agents={}", subagentRefs.size());
        return baseSysPrompt + rules;
    }

    /**
     * 启动阶段传播 maxContextTokens 配置：父 Agent 配置了该值，子 Agent 未配置则自动继承。
     */
    private void propagateMaxContextTokens() {
        for (var entry : properties.getAgents().entrySet()) {
            String parentKey = entry.getKey();
            JAgentProperties.AgentConfig parentConfig = entry.getValue();
            int parentMaxTokens = parentConfig.getMaxContextTokens();

            if (parentMaxTokens <= 0) continue;

            List<JAgentProperties.SubagentRef> subagents = parentConfig.getSubagents();
            if (subagents == null || subagents.isEmpty()) continue;

            for (JAgentProperties.SubagentRef subRef : subagents) {
                String subKey = subRef.getId();
                JAgentProperties.AgentConfig subConfig = properties.getAgents().get(subKey);
                if (subConfig != null && subConfig.getMaxContextTokens() <= 0) {
                    subConfig.setMaxContextTokens(parentMaxTokens);
                    log.info("子 Agent [{}] 继承父 Agent [{}] 的 maxContextTokens={}", subKey, parentKey, parentMaxTokens);
                }
            }
        }
    }

    /**
     * 配置权限引擎，支持用户确认机制。
     *
     * <p>当配置了 requireConfirmTools 时，为指定工具添加 ASK 规则，
     * 执行这些工具前会发出 RequireUserConfirmEvent 暂停等待用户确认。
     */
    private void configurePermission(HarnessAgent.Builder builder, JAgentProperties.AgentConfig config, String agentKey) {
        List<String> requireConfirmTools = config.getRequireConfirmTools();
        String permissionModeStr = config.getPermissionMode();

        // 如果没有配置需要确认的工具，且使用默认权限模式，则不配置权限引擎
        if ((requireConfirmTools == null || requireConfirmTools.isEmpty())
                && ("default".equals(permissionModeStr) || permissionModeStr == null)) {
            return;
        }

        PermissionMode mode = PermissionMode.DEFAULT;
        if (permissionModeStr != null && !permissionModeStr.isBlank()) {
            try {
                mode = PermissionMode.fromString(permissionModeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Agent [{}] 无效的 permissionMode: {}，使用默认值 default", agentKey, permissionModeStr);
            }
        }

        PermissionContextState.Builder permBuilder = PermissionContextState.builder().mode(mode);

        // 为需要确认的工具添加 ASK 规则
        if (requireConfirmTools != null && !requireConfirmTools.isEmpty()) {
            for (String toolName : requireConfirmTools) {
                // ASK 规则会在执行前触发 RequireUserConfirmEvent
                PermissionRule rule = new PermissionRule(toolName, null, PermissionBehavior.ASK, "config");
                permBuilder.addAskRule(toolName, rule);
                log.info("Agent [{}] 工具 [{}] 已配置用户确认", agentKey, toolName);
            }
        }

        builder.permissionContext(permBuilder.build());
        log.info("Agent [{}] 已配置权限引擎，mode={}", agentKey, mode);
    }
}
