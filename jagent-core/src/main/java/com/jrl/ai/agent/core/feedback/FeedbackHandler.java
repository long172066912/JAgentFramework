package com.jrl.ai.agent.core.feedback;

/**
 * 反馈处理器 — 接收并处理反馈
 */
public interface FeedbackHandler {

    /**
     * 处理反馈
     */
    void handle(Feedback feedback);

    /**
     * 是否处理该类型的反馈目标
     */
    boolean accepts(FeedbackTarget target);
}
