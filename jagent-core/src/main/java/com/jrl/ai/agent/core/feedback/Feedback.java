package com.jrl.ai.agent.core.feedback;

import java.time.Instant;
import java.util.Map;

/**
 * 反馈条目 — 通用的反馈数据载体。
 *
 * <p>记录对 Agent 执行结果的评价信息，包括评分、评论和元数据。
 * 通过 {@link FeedbackTarget} 指定反馈作用对象，
 * 通过 {@link FeedbackType} 区分反馈来源。
 *
 * @see FeedbackHandler
 */
public record Feedback(
        /** 反馈唯一标识 */
        String id,
        /** 产生该反馈的 Agent ID */
        String agentId,
        /** 关联的会话 ID */
        String sessionId,
        /** 反馈作用对象（提示词/Skill/输出/计划） */
        FeedbackTarget target,
        /** 反馈类型（显式/隐式/自动评估） */
        FeedbackType type,
        /** 评分（0.0 ~ 1.0） */
        double score,
        /** 用户或系统的文字评论，可为空 */
        String comment,
        /** 扩展元数据 */
        Map<String, Object> metadata,
        /** 反馈创建时间 */
        Instant createdAt
) {

    /**
     * 快速创建反馈实例。
     *
     * @param agentId   Agent ID
     * @param sessionId 会话 ID
     * @param target    反馈目标
     * @param type      反馈类型
     * @param score     评分
     * @return 新建的 Feedback 实例
     */
    public static Feedback of(String agentId, String sessionId, FeedbackTarget target,
                              FeedbackType type, double score) {
        return new Feedback(
                java.util.UUID.randomUUID().toString(),
                agentId, sessionId, target, type, score, null, Map.of(), Instant.now()
        );
    }
}
