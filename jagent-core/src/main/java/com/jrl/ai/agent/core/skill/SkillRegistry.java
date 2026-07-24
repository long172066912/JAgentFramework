package com.jrl.ai.agent.core.skill;

import java.util.Collection;
import java.util.Optional;

/**
 * Skill 注册表 — 管理所有可用技能
 */
public interface SkillRegistry {

    void register(Skill skill);

    Optional<Skill> get(String name);

    Collection<Skill> all();

    void unregister(String name);
}
