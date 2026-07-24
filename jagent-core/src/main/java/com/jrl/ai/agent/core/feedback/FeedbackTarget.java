package com.jrl.ai.agent.core.feedback;

/**
 * 反馈目标 — 反馈作用于什么对象
 */
public enum FeedbackTarget {
    /** 作用于提示词 */
    PROMPT,
    /** 作用于 Skill */
    SKILL,
    /** 作用于整体输出 */
    OUTPUT,
    /** 作用于计划 */
    PLAN
}
