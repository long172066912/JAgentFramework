package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.agent.AgentLifecycleManager;
import com.jrl.ai.agent.agentscope.model.AgentScopeModelRegistry;
import com.jrl.ai.agent.agentscope.prompt.InMemoryPromptRegistry;
import com.jrl.ai.agent.agentscope.router.DefaultRouter;
import com.jrl.ai.agent.agentscope.skill.SkillScoringInterceptor;
import com.jrl.ai.agent.agentscope.storage.JsonFileKVStore;
import com.jrl.ai.agent.agentscope.adapter.AgentScopeModelAdapter;
import com.jrl.ai.agent.agentscope.model.OpenAICompatibleModel;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import com.jrl.ai.agent.agentscope.evaluation.*;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.agent.AgentLifecycle;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.feedback.OutputFeedbackHandler;
import com.jrl.ai.agent.core.model.ModelRegistry;
import com.jrl.ai.agent.core.monitor.MetricsInterceptor;
import com.jrl.ai.agent.core.plan.Planner;
import com.jrl.ai.agent.core.prompt.PromptRegistry;
import com.jrl.ai.agent.core.retrieval.Retriever;
import com.jrl.ai.agent.core.router.Router;
import com.jrl.ai.agent.core.skill.SkillRegistry;
import com.jrl.ai.agent.core.skill.SkillScorer;
import com.jrl.ai.agent.core.storage.KVStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JAgent Spring Boot 自动装配。
 *
 * <p>当 classpath 中存在 Spring Boot 时，自动注册所有核心 Bean：
 * <ul>
 *   <li>{@link AgentFactory} — 同时作为 {@link AgentRegistry}</li>
 *   <li>{@link MetricsInterceptor} — 条件化，仅当 classpath 有 {@link MeterRegistry} 时生效</li>
 *   <li>{@link AgentScopeModelRegistry} — 作为 {@link ModelRegistry}</li>
 *   <li>{@link InMemoryPromptRegistry} — 作为 {@link PromptRegistry}</li>
 *   <li>{@link JsonFileKVStore} — 作为 {@link KVStore}</li>
 *   <li>{@link DefaultRouter} — 作为 {@link Router}</li>
 *   <li>{@link AgentLifecycleManager} — 作为 {@link AgentLifecycle}</li>
 *   <li>{@link SkillScoringInterceptor} — 作为 {@link SkillScorer}，Skill 评分拦截器</li>
 *   <li>{@link com.jrl.ai.agent.agentscope.plan.AgentScopePlanner} — 作为 {@link Planner}，可选</li>
 *   <li>{@link com.jrl.ai.agent.agentscope.retrieval.AgentScopeRetriever} — 作为 {@link Retriever}，条件化</li>
 * </ul>
 *
 * <p>使用方式：在 Spring Boot 应用中 {@code @Import(JAgentAutoConfiguration.class)} 即可。
 */
@Configuration
@EnableConfigurationProperties(JAgentProperties.class)
public class JAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JAgentAutoConfiguration.class);

    /**
     * 注册 Agent 工厂 Bean，同时作为 {@link AgentRegistry}。
     *
     * @param properties    JAgent 配置属性
     * @param interceptors  同步拦截器列表
     * @param skillRegistry Skill 注册表（可选）
     * @return AgentFactory 实例
     */
    @Bean
    public AgentFactory agentFactory(JAgentProperties properties,
                                     List<AgentInterceptor> interceptors,
                                     ObjectProvider<SkillRegistry> skillRegistry) {
        log.info("[AgentFactory] Creating AgentFactory with {} sync interceptors",
                interceptors.size());
        SkillRegistry registry = skillRegistry.getIfAvailable();
        return new AgentFactory(properties, interceptors, registry);
    }

    /**
     * 注册 Micrometer 指标拦截器（仅当 classpath 存在 MeterRegistry 时）。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return MetricsInterceptor 实例
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public MetricsInterceptor metricsInterceptor(ObjectProvider<MeterRegistry> meterRegistry) {
        return new MetricsInterceptor(meterRegistry.getIfAvailable());
    }

    /**
     * 注册模型注册表 — 桥接 AgentScope ModelRegistry。
     *
     * @return AgentScopeModelRegistry 实例
     */
    @Bean
    public ModelRegistry modelRegistry() {
        return new AgentScopeModelRegistry();
    }

    /**
     * 注册提示词注册表 — 内存实现。
     *
     * @return InMemoryPromptRegistry 实例
     */
    @Bean
    public PromptRegistry promptRegistry() {
        return new InMemoryPromptRegistry();
    }

    /**
     * 注册 KV 存储 — 文件持久化实现。
     *
     * @param properties JAgent 配置属性（取 workspace 路径）
     * @return JsonFileKVStore 实例
     */
    @Bean
    public KVStore kvStore(JAgentProperties properties) {
        return new JsonFileKVStore(Path.of(properties.getWorkspace(), "kvstore"));
    }

    /**
     * 注册默认路由器。
     *
     * @param agentRegistry Agent 注册表
     * @return DefaultRouter 实例
     */
    @Bean
    public Router router(AgentRegistry agentRegistry) {
        return new DefaultRouter(agentRegistry);
    }

    /**
     * 注册 Agent 生命周期管理器。
     *
     * @param agentRegistry Agent 注册表
     * @return AgentLifecycleManager 实例
     */
    @Bean
    public AgentLifecycle agentLifecycle(AgentRegistry agentRegistry) {
        return new AgentLifecycleManager(agentRegistry);
    }

    /**
     * 注册 AgentScope 规划器（可选，通过 {@code jagent.planner.enabled=true} 启用）。
     *
     * @param properties JAgent 配置属性
     * @return AgentScopePlanner 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.planner.enabled", havingValue = "true")
    public Planner planner(JAgentProperties properties) {
        String modelRef = properties.getAgents().values().stream()
                .findFirst()
                .map(JAgentProperties.AgentConfig::getModel)
                .orElse("dashscope:qwen-plus");
        return new com.jrl.ai.agent.agentscope.plan.AgentScopePlanner(
                Path.of(properties.getWorkspace()), modelRef);
    }

    /**
     * 注册 AgentScope 检索器（仅当容器中存在 AgentScope Knowledge Bean 时）。
     *
     * @param knowledge AgentScope 知识源
     * @return AgentScopeRetriever 实例
     */
    @Bean
    @ConditionalOnBean(io.agentscope.core.rag.Knowledge.class)
    public Retriever retriever(io.agentscope.core.rag.Knowledge knowledge) {
        return new com.jrl.ai.agent.agentscope.retrieval.AgentScopeRetriever(knowledge);
    }

    /**
     * 注册 Skill 评分拦截器 — 同时作为 {@link SkillScorer}。
     *
     * <p>在 Skill 执行前后自动采集 (agentId, skillName) 维度的执行统计，
     * 结合外部配置的静态优先级计算评分。
     *
     * @param properties JAgent 配置属性
     * @return SkillScoringInterceptor 实例
     */
    @Bean
    public SkillScoringInterceptor skillScoringInterceptor(JAgentProperties properties) {
        // 从每个 Agent 的配置中提取 Skill 优先级
        Map<String, Map<String, Double>> priorities = new HashMap<>();
        properties.getAgents().forEach((agentId, config) -> {
            if (!config.getSkillPriorities().isEmpty()) {
                priorities.put(agentId, config.getSkillPriorities());
            }
        });
        return new SkillScoringInterceptor(priorities);
    }

    /**
     * 注册 Skill 评分器（默认使用 SkillScoringInterceptor 实例）。
     *
     * @param interceptor Skill 评分拦截器
     * @return SkillScorer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SkillScorer skillScorer(SkillScoringInterceptor interceptor) {
        return interceptor;
    }

    // ==================== 评测体系 ====================

    /**
     * 注册规则评测器（Tier1，零成本）。
     *
     * @param properties JAgent 配置属性
     * @return RuleBasedEvaluator 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.enabled", havingValue = "true")
    public RuleBasedEvaluator ruleBasedEvaluator(JAgentProperties properties) {
        return new RuleBasedEvaluator(properties.getEvaluation().getLatencyThresholdMs());
    }

    /**
     * 注册 LLM 评测器（Tier2，按需开启）。
     *
     * @param properties    JAgent 配置属性
     * @param modelRegistry 模型注册表
     * @return LLMJudgeEvaluator 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.llm-judge-enabled", havingValue = "true")
    public LLMJudgeEvaluator llmJudgeEvaluator(JAgentProperties properties) {
        String modelRef = properties.getEvaluation().getLlmJudgeModel();
        String customPrompt = properties.getEvaluation().getLlmJudgePrompt();
        com.jrl.ai.agent.core.model.Model model = buildModelFromConfig(modelRef, properties);
        log.info("[LLMJudgeEvaluator] model={}, customPrompt={}", modelRef, customPrompt != null);
        return new LLMJudgeEvaluator(model, customPrompt);
    }

    /**
     * 从 jagent.model 配置构建 Model（与 AgentFactory.buildModel 逻辑一致）。
     */
    private static com.jrl.ai.agent.core.model.Model buildModelFromConfig(
            String modelRef, JAgentProperties properties) {
        int colonIdx = modelRef.indexOf(':');
        if (colonIdx <= 0) {
            return new AgentScopeModelAdapter(modelRef);
        }
        String provider = modelRef.substring(0, colonIdx);
        String modelName = modelRef.substring(colonIdx + 1);
        String apiKey = properties.getModel().getApiKeys().get(provider);
        String baseUrl = properties.getModel().getBaseUrls().get(provider);

        if (apiKey == null || apiKey.isBlank()) {
            return new AgentScopeModelAdapter(modelRef);
        }

        io.agentscope.core.model.Model delegate = switch (provider.toLowerCase()) {
            case "dashscope" -> DashScopeChatModel.builder()
                    .apiKey(apiKey).modelName(modelName).stream(true).baseUrl(baseUrl).build();
            case "openai" -> new OpenAICompatibleModel(apiKey, modelName, baseUrl, true, false);
            default -> {
                log.warn("LLMJudge: 暂不支持自动构建 {} 的 Model，回退到环境变量方式", provider);
                yield null;
            }
        };
        if (delegate == null) {
            return new AgentScopeModelAdapter(modelRef);
        }
        return new AgentScopeModelAdapter(delegate, provider);
    }

    /**
     * 注册复合评分器（默认加权实现，可自定义替换）。
     *
     * @param properties JAgent 配置属性
     * @return CompositeScorer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "jagent.evaluation.enabled", havingValue = "true")
    public CompositeScorer compositeScorer(JAgentProperties properties) {
        Map<String, Double> configWeights = properties.getEvaluation().getWeights();
        if (configWeights != null && !configWeights.isEmpty()) {
            Map<EvaluationDimension, Double> weights = new EnumMap<>(EvaluationDimension.class);
            configWeights.forEach((k, v) -> {
                try {
                    weights.put(EvaluationDimension.valueOf(k.toUpperCase()), v);
                } catch (IllegalArgumentException ignored) {
                    // 忽略未知维度
                }
            });
            return new DefaultCompositeScorer(weights);
        }
        return new DefaultCompositeScorer();
    }

    /**
     * 注册评测结果存储（JSON 文件实现）。
     *
     * @param properties JAgent 配置属性
     * @return EvaluationStore 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.enabled", havingValue = "true")
    public EvaluationStore evaluationStore(JAgentProperties properties) {
        return new JsonFileEvaluationStore(Path.of(properties.getWorkspace(), "evaluation"));
    }

    /**
     * 注册输出反馈处理器。
     *
     * @param store 评测结果存储
     * @return OutputFeedbackHandler 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.enabled", havingValue = "true")
    public OutputFeedbackHandler outputFeedbackHandler(EvaluationStore store) {
        return new DefaultOutputFeedbackHandler(store);
    }

    /**
     * 注册优化报告存储（JSON 文件实现）。
     *
     * @param properties JAgent 配置属性
     * @return OptimizationReportStore 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.enabled", havingValue = "true")
    public OptimizationReportStore optimizationReportStore(JAgentProperties properties) {
        return new JsonFileOptimizationReportStore(
                Path.of(properties.getWorkspace(), "evaluation", "optimization"));
    }

    /**
     * 注册基于规则的优化分析器（默认，LLM 分析器未启用时生效）。
     *
     * @return RuleBasedOptimizationAnalyzer 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.optimization.llm-enabled",
            havingValue = "false", matchIfMissing = true)
    public OptimizationAnalyzer ruleBasedOptimizationAnalyzer() {
        return new RuleBasedOptimizationAnalyzer();
    }

    /**
     * 注册基于 LLM 的优化分析器（可选，通过 {@code jagent.evaluation.optimization.llm-enabled=true} 启用）。
     *
     * @param properties    JAgent 配置属性
     * @param modelRegistry 模型注册表
     * @return LlmOptimizationAnalyzer 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.optimization.llm-enabled", havingValue = "true")
    public OptimizationAnalyzer llmOptimizationAnalyzer(JAgentProperties properties) {
        String modelRef = properties.getEvaluation().getLlmJudgeModel();
        com.jrl.ai.agent.core.model.Model model = buildModelFromConfig(modelRef, properties);
        return new LlmOptimizationAnalyzer(model);
    }

    /**
     * 注册评测拦截器，自动收集所有 Evaluator Bean。
     *
     * @param properties              JAgent 配置属性
     * @param evaluators              所有已注册的评测器
     * @param compositeScorer         复合评分器
     * @param store                   评测结果存储
     * @param optimizationAnalyzer    优化分析器（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     * @return EvaluationInterceptor 实例
     */
    @Bean
    @ConditionalOnProperty(name = "jagent.evaluation.enabled", havingValue = "true")
    public EvaluationInterceptor evaluationInterceptor(
            JAgentProperties properties,
            ObjectProvider<Evaluator> evaluators,
            CompositeScorer compositeScorer,
            EvaluationStore store,
            ObjectProvider<OptimizationAnalyzer> optimizationAnalyzer,
            ObjectProvider<OptimizationReportStore> optimizationReportStore) {
        List<Evaluator> evaluatorList = evaluators.orderedStream().toList();
        double confidenceThreshold = properties.getEvaluation().getOptimization().getConfidenceThreshold();
        log.info("[EvaluationInterceptor] Creating with {} evaluators, confidenceThreshold={}",
                evaluatorList.size(), confidenceThreshold);
        return new EvaluationInterceptor(evaluatorList, compositeScorer, store,
                optimizationAnalyzer.getIfAvailable(), optimizationReportStore.getIfAvailable(),
                confidenceThreshold);
    }

    /**
     * 注册 Agent 通用响应构建器。
     *
     * <p>提供 trace、tokenUsage、evaluation、optimization 的标准化序列化与查询能力，
     * 任何 Agent 服务均可注入此 Bean 构建统一 API 响应。
     *
     * @param evaluationStore        评测结果存储（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     * @return AgentResponseHelper 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentResponseHelper agentResponseHelper(
            ObjectProvider<EvaluationStore> evaluationStore,
            ObjectProvider<OptimizationReportStore> optimizationReportStore) {
        return new AgentResponseHelper(
                evaluationStore.getIfAvailable(),
                optimizationReportStore.getIfAvailable()
        );
    }

    /**
     * 注册 Agent 通用执行器 — 纯执行引擎，同步/责任链双通道。
     *
     * <p>评测由拦截器（AOP）自动处理，AgentExecutor 不关心评测逻辑。
     *
     * @param agentFactory            Agent 工厂
     * @param evaluationStore         评测结果存储（可选，用于查询结果）
     * @param optimizationReportStore 优化报告存储（可选，用于查询结果）
     * @return AgentExecutor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentExecutor agentExecutor(
            AgentFactory agentFactory,
            ObjectProvider<EvaluationStore> evaluationStore,
            ObjectProvider<OptimizationReportStore> optimizationReportStore) {
        return new AgentExecutor(
                agentFactory,
                evaluationStore.getIfAvailable(),
                optimizationReportStore.getIfAvailable()
        );
    }
}
