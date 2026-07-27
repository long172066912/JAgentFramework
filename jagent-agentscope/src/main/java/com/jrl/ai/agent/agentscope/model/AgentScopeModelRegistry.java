package com.jrl.ai.agent.agentscope.model;

import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.model.ModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 模型注册表 — 桥接 jagent-core {@link ModelRegistry} 与 AgentScope ModelRegistry。
 *
 * <p>内部维护本地缓存，注册时同步到 AgentScope 全局注册表，
 * 解析时优先查本地缓存，再 fallback 到 AgentScope。
 */
public class AgentScopeModelRegistry implements ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeModelRegistry.class);

    private final ConcurrentHashMap<String, Model> localCache = new ConcurrentHashMap<>();
    private volatile Model defaultModel;

    @Override
    public void register(Model model) {
        localCache.put(model.modelId(), model);
        if (defaultModel == null) {
            defaultModel = model;
        }
        // 同步到 AgentScope 全局注册表
        try {
            io.agentscope.core.model.ModelRegistry.register(model.modelId(),
                    ((com.jrl.ai.agent.agentscope.adapter.AgentScopeModelAdapter) model).getDelegate());
        } catch (Exception e) {
            log.debug("同步到 AgentScope ModelRegistry 失败（非 AgentScope 适配器）: {}", model.modelId());
        }
        log.info("注册模型: {}", model.modelId());
    }

    @Override
    public Optional<Model> resolve(String modelRef) {
        // 先查本地缓存
        Model local = localCache.get(modelRef);
        if (local != null) {
            return Optional.of(local);
        }
        // 尝试 AgentScope 全局解析
        try {
            if (io.agentscope.core.model.ModelRegistry.canResolve(modelRef)) {
                io.agentscope.core.model.Model asModel =
                        io.agentscope.core.model.ModelRegistry.resolve(modelRef);
                Model adapter = new com.jrl.ai.agent.agentscope.adapter.AgentScopeModelAdapter(asModel,
                        modelRef.contains(":") ? modelRef.split(":")[0] : "unknown");
                localCache.put(modelRef, adapter);
                return Optional.of(adapter);
            }
        } catch (Exception e) {
            log.debug("AgentScope ModelRegistry 解析失败: {}", modelRef);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Model> defaultModel() {
        return Optional.ofNullable(defaultModel);
    }

    @Override
    public Collection<Model> all() {
        return List.copyOf(localCache.values());
    }
}
