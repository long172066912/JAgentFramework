package com.jrl.ai.agent.demo.config;

import com.jrl.ai.agent.core.monitor.MetricsInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置 — 注册 Micrometer {@link MetricsInterceptor} 到 Spring 容器。
 *
 * <p>{@link MetricsInterceptor} 同时实现了 AgentInterceptor、SkillInterceptor、MemoryInterceptor，
 * Spring 会自动将其注入到 AgentFactory 的拦截器链中。
 *
 * <p>指标通过 Spring Boot Actuator 的 {@code /actuator/metrics} 端点暴露。
 *
 * @see MetricsInterceptor
 */
@Configuration
public class MetricsConfig {

    /**
     * 创建并注册 Micrometer 指标拦截器。
     *
     * <p>该拦截器负责采集 Agent 执行、Skill 调用、Memory 读写等环节的耗时与计数指标，
     * 并通过注入的 {@link MeterRegistry} 进行指标上报。
     *
     * @param meterRegistry Micrometer 指标注册表，由 Spring Boot Actuator 自动配置
     * @return 已初始化的 {@link MetricsInterceptor} 实例
     */
    @Bean
    public MetricsInterceptor metricsInterceptor(MeterRegistry meterRegistry) {
        return new MetricsInterceptor(meterRegistry);
    }
}
