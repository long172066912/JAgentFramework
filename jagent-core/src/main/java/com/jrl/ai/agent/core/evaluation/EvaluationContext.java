package com.jrl.ai.agent.core.evaluation;

import com.jrl.ai.agent.core.task.ExecutionTrace;

import java.util.Map;

/**
 * 评测上下文 — 评测器的输入数据。
 *
 * <p>包含 Agent 执行后的完整上下文信息，评测器据此打分。
 *
 * @param agentId    Agent 标识
 * @param sessionId  会话 ID（可选）
 * @param input      用户输入
 * @param output     Agent 输出
 * @param trace      执行链路追踪
 * @param taskResult 任务结果（可选，用于获取状态/耗时等）
 * @param metadata   附加元数据
 */
public record EvaluationContext(
        String agentId,
        String sessionId,
        String input,
        String output,
        ExecutionTrace trace,
        Map<String, Object> taskResult,
        Map<String, Object> metadata
) {

    /**
     * 快速创建评测上下文（无附加元数据）。
     *
     * @param agentId Agent 标识
     * @param input   用户输入
     * @param output  Agent 输出
     * @param trace   执行链路追踪
     * @return 评测上下文实例
     */
    public static EvaluationContext of(String agentId, String input, String output, ExecutionTrace trace) {
        return new EvaluationContext(agentId, null, input, output, trace, Map.of(), Map.of());
    }
}
