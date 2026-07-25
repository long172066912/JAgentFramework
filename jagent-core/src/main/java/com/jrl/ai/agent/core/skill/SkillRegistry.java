package com.jrl.ai.agent.core.skill;

import java.util.Collection;
import java.util.Optional;

/**
 * Skill 注册表 — 管理所有可用技能的注册、查找与注销。
 *
 * @see Skill
 */
public interface SkillRegistry {

    /**
     * 注册一个技能。
     *
     * @param skill 待注册的技能
     */
    void register(Skill skill);

    /**
     * 按名称查找技能。
     *
     * @param name 技能名称
     * @return 匹配的技能，不存在时返回 {@link Optional#empty()}
     */
    Optional<Skill> get(String name);

    /**
     * 获取所有已注册的技能。
     *
     * @return 不可变的技能集合
     */
    Collection<Skill> all();

    /**
     * 注销指定名称的技能。
     *
     * @param name 技能名称
     */
    void unregister(String name);
}
