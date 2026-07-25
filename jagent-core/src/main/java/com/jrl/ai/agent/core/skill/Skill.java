package com.jrl.ai.agent.core.skill;

/**
 * Skill — Agent 可具备的技能抽象。
 *
 * <p>Skill 是 Agent 执行能力的封装，每个 Skill 拥有名称、描述和执行逻辑。
 * 通过 {@link SkillRegistry} 注册后可被 Agent 动态调用，
 * 通过 {@link SkillInterceptor} 可在执行前后插入拦截逻辑。
 *
 * @see SkillContext
 * @see SkillResult
 */
public interface Skill {

    /**
     * 获取技能名称。
     *
     * @return 技能唯一名称
     */
    String name();

    /**
     * 获取技能描述（供 LLM 理解技能用途）。
     *
     * @return 技能描述文本
     */
    String description();

    /**
     * 执行技能。
     *
     * @param context 技能执行上下文
     * @return 执行结果
     */
    SkillResult execute(SkillContext context);

    /**
     * 技能是否可用。
     *
     * <p>默认返回 {@code true}，实现方可根据资源状态、
     * 权限等条件判断是否可用。
     *
     * @return 若技能当前可执行则返回 {@code true}
     */
    default boolean isAvailable() {
        return true;
    }
}
