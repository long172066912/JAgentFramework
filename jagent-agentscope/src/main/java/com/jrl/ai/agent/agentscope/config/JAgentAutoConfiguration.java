package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.core.agent.AgentInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * JAgent Spring Boot 自动装配。
 *
 * <p>当 classpath 中存在 Spring Boot 时，自动注册 {@link JAgentProperties} 和 {@link AgentFactory}。
 * 自动注入所有 {@link AgentInterceptor} Bean 到 Agent 执行链路中。
 *
 * <p>使用方式：在 Spring Boot 应用中 {@code @Import(JAgentAutoConfiguration.class)} 即可。
 *
 * <p>如果不使用 Spring Boot，可直接 new {@link JAgentProperties} + {@link AgentFactory}。
 */
@Configuration
@EnableConfigurationProperties(JAgentProperties.class)
public class JAgentAutoConfiguration {

    /**
     * 注册 Agent 工厂 Bean，自动注入所有 AgentInterceptor。
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
}
