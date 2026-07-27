package com.jrl.ai.agent.core.monitor;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.memory.MemoryInterceptor;
import com.jrl.ai.agent.core.memory.MemoryStore;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillInterceptor;
import com.jrl.ai.agent.core.skill.SkillResult;
import com.jrl.ai.agent.core.task.TaskResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的统一监控拦截器 — 同时实现 Agent、Skill、Memory 三类拦截器。
 *
 * <p>自动采集以下指标：
 * <ul>
 *   <li>{@code jagent.agent.execution} — Agent 执行耗时 Timer（tags: agentId, status）</li>
 *   <li>{@code jagent.skill.execution} — Skill 执行耗时 Timer（tags: skillName, status）</li>
 *   <li>{@code jagent.memory.operation} — Memory 操作耗时 Timer（tags: operation, namespace）</li>
 *   <li>{@code jagent.agent.token} — Token 消耗 Counter（tags: agentId, model, type）</li>
 * </ul>
 */
public class MetricsInterceptor implements AgentInterceptor, SkillInterceptor, MemoryInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MetricsInterceptor.class);

    private final MeterRegistry registry;

    public MetricsInterceptor(MeterRegistry registry) {
        this.registry = registry;
    }

    // ==================== Agent 拦截 ====================

    @Override
    public void beforeExecute(Agent agent, ChatMessage input, AgentContext context) {
        log.debug("[Metrics] agent={} execute start", agent.id());
    }

    @Override
    public void afterExecute(Agent agent, ChatMessage input, AgentContext context, TaskResult result) {
        Timer.builder("jagent.agent.execution")
                .tag("agentId", agent.id())
                .tag("agentName", agent.name())
                .tag("status", result.isSuccess() ? "success" : "failure")
                .register(registry)
                .record(result.durationMs(), TimeUnit.MILLISECONDS);

        // Token 消耗统计
        if (result.usage() != null) {
            registry.counter("jagent.agent.token.prompt",
                    "agentId", agent.id(),
                    "model", result.usage().modelId()
            ).increment(result.usage().promptTokens());

            registry.counter("jagent.agent.token.completion",
                    "agentId", agent.id(),
                    "model", result.usage().modelId()
            ).increment(result.usage().completionTokens());

            registry.counter("jagent.agent.token.total",
                    "agentId", agent.id(),
                    "model", result.usage().modelId()
            ).increment(result.usage().totalTokens());
        }

        log.info("[Metrics] agent={} duration={}ms status={}",
                agent.id(), result.durationMs(), result.isSuccess() ? "OK" : "FAIL");
    }

    @Override
    public void onError(Agent agent, ChatMessage input, AgentContext context, Throwable error) {
        registry.counter("jagent.agent.error",
                "agentId", agent.id(),
                "error", error.getClass().getSimpleName()
        ).increment();

        log.warn("[Metrics] agent={} error={}", agent.id(), error.getMessage());
    }

    // ==================== Skill 拦截 ====================

    @Override
    public void beforeExecute(Skill skill, SkillContext context) {
        log.debug("[Metrics] skill={} execute start", skill.name());
    }

    @Override
    public void afterExecute(Skill skill, SkillContext context, SkillResult result) {
        Timer.builder("jagent.skill.execution")
                .tag("skillName", skill.name())
                .tag("status", result.success() ? "success" : "failure")
                .register(registry)
                .record(result.durationMs(), TimeUnit.MILLISECONDS);

        log.info("[Metrics] skill={} duration={}ms status={}",
                skill.name(), result.durationMs(), result.success() ? "OK" : "FAIL");
    }

    @Override
    public void onError(Skill skill, SkillContext context, Throwable error) {
        registry.counter("jagent.skill.error",
                "skillName", skill.name(),
                "error", error.getClass().getSimpleName()
        ).increment();

        log.warn("[Metrics] skill={} error={}", skill.name(), error.getMessage());
    }

    // ==================== Memory 拦截 ====================

    @Override
    public void beforePut(MemoryStore store, String namespace, String key, String value) {
        recordMemoryOp("put", namespace);
    }

    @Override
    public void beforeGet(MemoryStore store, String namespace, String key) {
        recordMemoryOp("get", namespace);
    }

    @Override
    public void beforeRemove(MemoryStore store, String namespace, String key) {
        recordMemoryOp("remove", namespace);
    }

    private void recordMemoryOp(String operation, String namespace) {
        registry.counter("jagent.memory.operation",
                "operation", operation,
                "namespace", namespace
        ).increment();

        log.debug("[Metrics] memory op={} namespace={}", operation, namespace);
    }
}
