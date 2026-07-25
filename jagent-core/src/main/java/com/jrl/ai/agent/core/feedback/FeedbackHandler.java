package com.jrl.ai.agent.core.feedback;

/**
 * 反馈处理器 — 接收并处理反馈的顶层接口。
 *
 * <p>具体实现通过 {@link #accepts(FeedbackTarget)} 声明关注的反馈目标类型，
 * 框架仅将匹配的反馈分发给对应处理器。
 *
 * @see PromptFeedbackHandler
 * @see SkillFeedbackHandler
 */
public interface FeedbackHandler {

    /**
     * 处理一条反馈记录。
     *
     * @param feedback 待处理的反馈
     */
    void handle(Feedback feedback);

    /**
     * 判断是否接受指定目标的反馈。
     *
     * @param target 反馈目标类型
     * @return 若该处理器关注此目标则返回 {@code true}
     */
    boolean accepts(FeedbackTarget target);
}
