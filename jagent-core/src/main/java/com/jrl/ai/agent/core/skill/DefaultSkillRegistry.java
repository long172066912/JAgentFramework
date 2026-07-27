package com.jrl.ai.agent.core.skill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Skill 注册表实现 — 基于 ConcurrentHashMap 的内存注册表。
 *
 * <p>线程安全，支持并发注册和查找。
 * 适用于单 Agent 或轻量级场景；
 * 生产环境可替换为持久化实现。
 *
 * @see SkillRegistry
 */
public class DefaultSkillRegistry implements SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @Override
    public void register(Skill skill) {
        Objects.requireNonNull(skill, "skill must not be null");
        Objects.requireNonNull(skill.name(), "skill.name() must not be null");
        skills.put(skill.name(), skill);
    }

    @Override
    public Optional<Skill> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    @Override
    public Collection<Skill> all() {
        return List.copyOf(skills.values());
    }

    @Override
    public void unregister(String name) {
        skills.remove(name);
    }
}
