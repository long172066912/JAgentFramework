package com.jrl.ai.agent.core.feedback;

import java.time.Instant;
import java.util.Map;

/**
 * 反馈条目 — 通用的反馈数据载体
 */
public record Feedback(
        String id,
        String agentId,
        String sessionId,
        FeedbackTarget target,
        FeedbackType type,
        double score,
        String comment,
        Map<String, Object> metadata,
        Instant createdAt
) {

    public static Feedback of(String agentId, String sessionId, FeedbackTarget target,
                              FeedbackType type, double score) {
        return new Feedback(
                java.util.UUID.randomUUID().toString(),
                agentId, sessionId, target, type, score, null, Map.of(), Instant.now()
        );
    }
}
