package com.jrl.ai.agent.core.skill;

/**
 * Skill 执行结果
 */
public record SkillResult(
        String skillName,
        boolean success,
        String output,
        String feedback,
        long durationMs
) {

    public static SkillResult success(String skillName, String output, long durationMs) {
        return new SkillResult(skillName, true, output, null, durationMs);
    }

    public static SkillResult failure(String skillName, String output, long durationMs) {
        return new SkillResult(skillName, false, output, null, durationMs);
    }
}
