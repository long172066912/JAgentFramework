package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.agent.AgentLifecycleManager;
import com.jrl.ai.agent.agentscope.model.AgentScopeModelRegistry;
import com.jrl.ai.agent.agentscope.prompt.InMemoryPromptRegistry;
import com.jrl.ai.agent.agentscope.router.DefaultRouter;
import com.jrl.ai.agent.agentscope.storage.JsonFileKVStore;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.agent.AgentLifecycle;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.model.ModelRegistry;
import com.jrl.ai.agent.core.monitor.MetricsInterceptor;
import com.jrl.ai.agent.core.plan.Planner;
import com.jrl.ai.agent.core.prompt.PromptRegistry;
import com.jrl.ai.agent.core.retrieval.Retriever;
import com.jrl.ai.agent.core.router.Router;
import com.jrl.ai.agent.core.storage.KVStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

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
 *   <li>{@link com.jrl.ai.agent.agentscope.plan.AgentScopePlanner} — 作为 {@link Planner}，可选</li>
 *   <li>{@link com.jrl.ai.agent.agentscope.retrieval.AgentScopeRetriever} — 作为 {@link Retriever}，条件化</li>
 * </ul>
 *
 * <p>使用方式：在 Spring Boot 应用中 {@code @Import(JAgentAutoConfiguration.class)} 即可。
 */
@Configuration
@EnableConfigurationProperties(JAgentProperties.class)
public class JAgentAutoConfiguration {

    /**
     * 注册 Agent 工厂 Bean，同时作为 {@link AgentRegistry}。
     *
     * @param properties   JAgent 配置属性
     * @param interceptors Agent 拦截器（Spring 自动收集所有 AgentInterceptor Bean）
     * @return AgentFactory 实例
     */
    @Bean
    public AgentFactory agentFactory(JAgentProperties properties,
                                     ObjectProvider<AgentInterceptor> interceptors) {
        List<AgentInterceptor> interceptorList = interceptors.orderedStream().toList();
        return new AgentFactory(properties, interceptorList);
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
}
