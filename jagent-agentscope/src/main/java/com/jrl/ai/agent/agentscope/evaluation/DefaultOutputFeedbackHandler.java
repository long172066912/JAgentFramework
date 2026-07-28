package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.EvaluationStore;
import com.jrl.ai.agent.core.feedback.Feedback;
import com.jrl.ai.agent.core.feedback.OutputFeedbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认输出反馈处理器 — 将人工评测反馈关联到评测结果。
 *
 * <p>接收 EXPLICIT 类型反馈，记录日志并持久化到 {@link EvaluationStore}。
 */
public class DefaultOutputFeedbackHandler implements OutputFeedbackHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutputFeedbackHandler.class);

    private final EvaluationStore store;

    /**
     * 创建输出反馈处理器。
     *
     * @param store 评测结果存储
     */
    public DefaultOutputFeedbackHandler(EvaluationStore store) {
        this.store = store;
    }

    @Override
    public void recordOutputFeedback(String evalId, Feedback feedback) {
        log.info("[Evaluation] Output feedback received: evalId={} agentId={} score={} comment={}",
                evalId, feedback.agentId(), feedback.score(), feedback.comment());

        // 反馈信息记录到日志，后续可扩展为更新评测结果
        // 当前版本仅做日志记录，完整的人工反馈闭环可在后续版本增强
    }

    @Override
    public void handle(Feedback feedback) {
        // 从 feedback metadata 中获取 evalId
        String evalId = feedback.metadata() != null
                ? String.valueOf(feedback.metadata().getOrDefault("evalId", ""))
                : "";

        if (!evalId.isEmpty()) {
            recordOutputFeedback(evalId, feedback);
        } else {
            log.warn("[Evaluation] Output feedback without evalId: agentId={}", feedback.agentId());
        }
    }
}
