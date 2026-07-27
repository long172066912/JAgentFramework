package com.jrl.ai.agent.agentscope.prompt;

import com.jrl.ai.agent.core.prompt.PromptRegistry;
import com.jrl.ai.agent.core.prompt.PromptTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存提示词注册表 — 基于 ConcurrentHashMap 的提示词模板管理。
 *
 * <p>支持同一模板的多版本管理，线程安全。
 */
public class InMemoryPromptRegistry implements PromptRegistry {

    /** name -> (version -> template) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PromptTemplate>> registry =
            new ConcurrentHashMap<>();

    @Override
    public void register(PromptTemplate template) {
        String version = (template instanceof SimplePromptTemplate spt) ? spt.version() : "latest";
        registry.computeIfAbsent(template.name(), k -> new ConcurrentHashMap<>())
                .put(version, template);
    }

    @Override
    public Optional<PromptTemplate> get(String name) {
        ConcurrentHashMap<String, PromptTemplate> versions = registry.get(name);
        if (versions == null || versions.isEmpty()) return Optional.empty();
        // 优先返回 "latest"，否则返回最新注册的版本
        PromptTemplate latest = versions.get("latest");
        if (latest != null) return Optional.of(latest);
        return Optional.of(versions.values().iterator().next());
    }

    @Override
    public Optional<PromptTemplate> get(String name, String version) {
        ConcurrentHashMap<String, PromptTemplate> versions = registry.get(name);
        if (versions == null) return Optional.empty();
        return Optional.ofNullable(versions.get(version));
    }

    @Override
    public void unregister(String name) {
        registry.remove(name);
    }

    /**
     * 获取所有已注册的模板名称。
     *
     * @return 模板名称集合
     */
    public Collection<String> allNames() {
        return List.copyOf(registry.keySet());
    }
}
