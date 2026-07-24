package com.jrl.ai.agent.core.feedback;

import com.jrl.ai.agent.core.skill.Skill;

/**
 * Skill 反馈处理器 — 根据反馈优化 Skill 选择与执行
 */
public interface SkillFeedbackHandler extends FeedbackHandler {

    /**
     * 记录 Skill 执行反馈，用于后续选择优化
     */
    void recordSkillFeedback(String skillName, Feedback feedback);

    /**
     * 获取 Skill 的评分（用于路由选择）
     */
    double getSkillScore(String skillName);

    @Override
    default boolean accepts(FeedbackTarget target) {
        return target == FeedbackTarget.SKILL;
    }
}
