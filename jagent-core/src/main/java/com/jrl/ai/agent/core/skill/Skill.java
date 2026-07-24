package com.jrl.ai.agent.core.skill;

/**
 * Skill — Agent 可具备的技能抽象
 */
public interface Skill {

    /**
     * 技能名称
     */
    String name();

    /**
     * 技能描述
     */
    String description();

    /**
     * 执行技能
     */
    SkillResult execute(SkillContext context);

    /**
     * 技能是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}
