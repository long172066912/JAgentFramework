package com.jrl.ai.agent.core.feedback;

/**
 * 反馈类型
 */
public enum FeedbackType {
    /** 用户显式反馈（点赞/点踩） */
    EXPLICIT,
    /** 系统隐式反馈（执行成功/失败） */
    IMPLICIT,
    /** 自动评估反馈 */
    AUTO_EVAL
}
