package com.jrl.ai.agent.demo.config;

import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.memory.MemoryInterceptor;
import com.jrl.ai.agent.core.monitor.MetricsInterceptor;
import com.jrl.ai.agent.core.skill.SkillInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置 — 注册 Micrometer MetricsInterceptor 到 Spring 容器。
 *
 * <p>MetricsInterceptor 同时实现 AgentInterceptor、SkillInterceptor、MemoryInterceptor，
 * Spring 会自动将其注入到 AgentFactory 的拦截器链中。
 *
 * <p>指标通过 Spring Boot Actuator 的 /actuator/metrics 端点暴露。
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MetricsInterceptor metricsInterceptor(MeterRegistry meterRegistry) {
        return new MetricsInterceptor(meterRegistry);
    }
}
