package com.jrl.ai.agent.core.feedback;

/**
 * 提示词反馈处理器 — 根据反馈优化提示词
 */
public interface PromptFeedbackHandler extends FeedbackHandler {

    /**
     * 根据反馈调整提示词
     * @return 优化后的提示词
     */
    String optimizePrompt(String currentPrompt, Feedback feedback);

    @Override
    default boolean accepts(FeedbackTarget target) {
        return target == FeedbackTarget.PROMPT;
    }
}
