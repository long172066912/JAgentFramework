package com.jrl.ai.agent.core.skill;

/**
 * Skill 执行结果 — 封装技能执行的输出与状态。
 *
 * @see Skill#execute(SkillContext)
 */
public record SkillResult(
        /** 产生该结果的技能名称 */
        String skillName,
        /** 是否执行成功 */
        boolean success,
        /** 输出文本 */
        String output,
        /** 反馈信息（用于反馈机制），可为空 */
        String feedback,
        /** 执行耗时（毫秒） */
        long durationMs
) {

    /**
     * 创建成功结果。
     *
     * @param skillName  技能名称
     * @param output     输出文本
     * @param durationMs 执行耗时
     * @return 成功结果
     */
    public static SkillResult success(String skillName, String output, long durationMs) {
        return new SkillResult(skillName, true, output, null, durationMs);
    }

    /**
     * 创建失败结果。
     *
     * @param skillName  技能名称
     * @param output     错误信息
     * @param durationMs 执行耗时
     * @return 失败结果
     */
    public static SkillResult failure(String skillName, String output, long durationMs) {
        return new SkillResult(skillName, false, output, null, durationMs);
    }
}
