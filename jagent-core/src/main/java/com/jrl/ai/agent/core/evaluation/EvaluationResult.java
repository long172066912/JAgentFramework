package com.jrl.ai.agent.core.evaluation;

import com.jrl.ai.agent.core.task.ExecutionTrace;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 评测结果 — 单次评测的完整输出。
 *
 * <p>包含各维度评分、加权总分、输入输出快照和执行链路。
 *
 * @param evalId         评测唯一 ID
 * @param agentId        Agent 标识
 * @param sessionId      会话 ID（可选）
 * @param scores         各维度评分（key = 维度名称）
 * @param compositeScore 加权总分（0.0 ~ 1.0）
 * @param trace          执行链路追踪
 * @param input          用户输入快照
 * @param output         Agent 输出快照
 * @param timestamp      评测时间
 */
public record EvaluationResult(
        String evalId,
        String agentId,
        String sessionId,
        Map<EvaluationDimension, DimensionScore> scores,
        double compositeScore,
        ExecutionTrace trace,
        String input,
        String output,
        Instant timestamp
) {

    /**
     * 创建评测结果 Builder。
     *
     * @param agentId Agent 标识
     * @return Builder 实例
     */
    public static Builder builder(String agentId) {
        return new Builder(agentId);
    }

    /**
     * 评测结果 Builder — 支持逐步构建评测结果。
     */
    public static class Builder {
        private final String evalId = UUID.randomUUID().toString();
        private final String agentId;
        private String sessionId;
        private Map<EvaluationDimension, DimensionScore> scores = Map.of();
        private double compositeScore;
        private ExecutionTrace trace;
        private String input;
        private String output;
        private Instant timestamp = Instant.now();

        Builder(String agentId) {
            this.agentId = agentId;
        }

        /**
         * 设置会话 ID。
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * 设置各维度评分。
         */
        public Builder scores(Map<EvaluationDimension, DimensionScore> scores) {
            this.scores = scores;
            return this;
        }

        /**
         * 设置加权总分。
         */
        public Builder compositeScore(double compositeScore) {
            this.compositeScore = compositeScore;
            return this;
        }

        /**
         * 设置执行链路追踪。
         */
        public Builder trace(ExecutionTrace trace) {
            this.trace = trace;
            return this;
        }

        /**
         * 设置用户输入快照。
         */
        public Builder input(String input) {
            this.input = input;
            return this;
        }

        /**
         * 设置 Agent 输出快照。
         */
        public Builder output(String output) {
            this.output = output;
            return this;
        }

        /**
         * 设置评测时间。
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 构建评测结果。
         */
        public EvaluationResult build() {
            return new EvaluationResult(
                    evalId, agentId, sessionId, scores,
                    compositeScore, trace, input, output, timestamp
            );
        }
    }
}
