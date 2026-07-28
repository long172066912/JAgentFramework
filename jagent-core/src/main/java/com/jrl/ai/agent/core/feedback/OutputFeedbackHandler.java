package com.jrl.ai.agent.core.feedback;

/**
 * 输出反馈处理器 — 处理 Agent 输出质量反馈。
 *
 * <p>用于接收人工评测反馈（EXPLICIT），关联到对应评测结果。
 */
public interface OutputFeedbackHandler extends FeedbackHandler {

    /**
     * 记录输出质量反馈。
     *
     * @param evalId   评测结果 ID
     * @param feedback 反馈数据
     */
    void recordOutputFeedback(String evalId, Feedback feedback);

    @Override
    default boolean accepts(FeedbackTarget target) {
        return target == FeedbackTarget.OUTPUT;
    }
}
