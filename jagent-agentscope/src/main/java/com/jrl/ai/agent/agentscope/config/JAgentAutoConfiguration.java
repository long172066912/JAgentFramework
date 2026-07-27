package com.jrl.ai.agent.agentscope.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JAgent Spring Boot 自动装配。
 *
 * <p>当 classpath 中存在 Spring Boot 时，自动注册 {@link JAgentProperties} 和 {@link AgentFactory}。
 * 使用方式：在 Spring Boot 应用中 {@code @Import(JAgentAutoConfiguration.class)} 即可。
 *
 * <p>如果不使用 Spring Boot，可直接 new {@link JAgentProperties} + {@link AgentFactory}。
 */
@Configuration
@EnableConfigurationProperties(JAgentProperties.class)
public class JAgentAutoConfiguration {

    /**
     * 注册 Agent 工厂 Bean。
     *
     * @param properties JAgent 配置属性
     * @return AgentFactory 实例
     */
    @Bean
    public AgentFactory agentFactory(JAgentProperties properties) {
        return new AgentFactory(properties);
    }
}
