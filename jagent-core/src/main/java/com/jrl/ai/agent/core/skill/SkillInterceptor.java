package com.jrl.ai.agent.core.skill;

/**
 * Skill 执行拦截器 — 在技能执行前后插入自定义逻辑。
 *
 * <p>典型用途包括：反馈采集、执行日志、权限校验、耗时监控等。
 * 所有方法均提供默认空实现，实现方按需覆写。
 *
 * @see Skill
 */
public interface SkillInterceptor {

    /**
     * 技能执行前调用。
     *
     * @param skill   即将执行的技能
     * @param context 执行上下文
     */
    default void beforeExecute(Skill skill, SkillContext context) {}

    /**
     * 技能执行后调用（可用于采集反馈）。
     *
     * @param skill   已执行完成的技能
     * @param context 执行上下文
     * @param result  执行结果
     */
    default void afterExecute(Skill skill, SkillContext context, SkillResult result) {}
}
