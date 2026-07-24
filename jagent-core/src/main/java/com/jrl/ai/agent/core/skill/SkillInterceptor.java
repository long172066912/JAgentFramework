package com.jrl.ai.agent.core.skill;

/**
 * Skill 执行拦截器 — 用于反馈采集
 */
public interface SkillInterceptor {

    /**
     * 执行前
     */
    default void beforeExecute(Skill skill, SkillContext context) {}

    /**
     * 执行后（可用于采集反馈）
     */
    default void afterExecute(Skill skill, SkillContext context, SkillResult result) {}
}
