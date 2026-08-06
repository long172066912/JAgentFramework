package com.jrl.ai.agent.agentscope.config;

import com.jrl.ai.agent.agentscope.tracing.EvaluationSpanProcessor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * JAgent OpenTelemetry 自动装配 — 初始化全局 TracerProvider。
 *
 * <p>默认开启（classpath 存在 OTel SDK 时生效），可通过配置关闭：
 * <pre>{@code
 * jagent:
 *   tracing:
 *     enabled: false          # 关闭 SDK 自动初始化（如已用 javaagent 方式接入）
 *     logging-exporter: false # 接入 OTLP/Jaeger 时关闭日志导出
 *     service-name: my-app    # 可选，默认取 spring.application.name
 * }</pre>
 *
 * <p>链路组成：
 * <pre>
 * jagent.execute &lt;agentKey&gt;          ← 框架根 span（适配器创建）
 * └─ invoke_agent &lt;name&gt;             ← AgentScope OtelTracingMiddleware
 *    ├─ chat &lt;model&gt;                 ← 每次模型调用（含 token 用量）
 *    ├─ execute_tool &lt;tool&gt;          ← 每次工具执行
 *    └─ agent.evaluation             ← 评测结果回写（各维度评分属性）
 * </pre>
 *
 * <p>评测启用时自动挂载 {@link EvaluationSpanProcessor}，
 * 评测体系据此捕获 span 做基于 trace 的多维度分析。
 * 若 {@code GlobalOpenTelemetry} 已被占用（如 javaagent），自动降级为本地实例。
 */
@Configuration
@ConditionalOnClass(SdkTracerProvider.class)
@ConditionalOnProperty(name = "jagent.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class JAgentOtelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JAgentOtelAutoConfiguration.class);

    /**
     * 构建并注册全局 OpenTelemetry SDK。
     *
     * <p>使用方已自定义 {@link OpenTelemetrySdk} Bean 时不再生成。
     *
     * @param properties              JAgent 配置属性
     * @param environment             Spring 环境（读取 spring.application.name）
     * @param evaluationSpanProcessor 评测链路 span 捕获器（评测启用时由框架自动装配）
     * @return OpenTelemetrySdk 实例（应用关闭时自动释放）
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public OpenTelemetrySdk openTelemetrySdk(
            JAgentProperties properties,
            Environment environment,
            ObjectProvider<EvaluationSpanProcessor> evaluationSpanProcessor) {
        JAgentProperties.TracingConfig tracing = properties.getTracing();

        String serviceName = tracing.getServiceName();
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = environment.getProperty("spring.application.name", "jagent");
        }
        Resource resource = Resource.getDefault().merge(Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));

        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        // 日志导出器（默认开启，接入 OTLP/Jaeger 时可通过 logging-exporter: false 关闭）
        if (tracing.isLoggingExporter()) {
            try {
                io.opentelemetry.exporter.logging.LoggingSpanExporter loggingExporter =
                        io.opentelemetry.exporter.logging.LoggingSpanExporter.create();
                tracerProviderBuilder.addSpanProcessor(
                        io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder(loggingExporter).build());
            } catch (NoClassDefFoundError e) {
                log.warn("[Otel] classpath 缺少 opentelemetry-exporter-logging，跳过日志导出器");
            }
        }

        // 评测链路捕获：span 结束后转为结构化数据供评测体系多维分析
        evaluationSpanProcessor.ifAvailable(processor -> {
            tracerProviderBuilder.addSpanProcessor(processor);
            log.info("[Otel] 已挂载 EvaluationSpanProcessor，评测体系将基于 trace 做多维分析");
        });

        OpenTelemetrySdk sdk;
        try {
            sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProviderBuilder.build())
                    .buildAndRegisterGlobal();
            log.info("[Otel] 全局 OpenTelemetry SDK 初始化完成, service.name={}", serviceName);
        } catch (IllegalStateException e) {
            // 全局已注册（如 javaagent 方式），回退为本地实例并告警
            log.warn("[Otel] GlobalOpenTelemetry 已被占用，AgentScope 链路将使用既有全局实例: {}", e.getMessage());
            sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProviderBuilder.build())
                    .build();
        }
        return sdk;
    }
}
