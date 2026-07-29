package com.jrl.ai.agent.core.task;

import com.jrl.ai.agent.core.evaluation.EvaluationResult;
import com.jrl.ai.agent.core.evaluation.OptimizationReport;
import com.jrl.ai.agent.core.task.contract.TokenUsage;

/**
 * Agent 通用执行响应 — 将业务数据与框架公共字段统一封装。
 *
 * <p>框架层自动采集 trace、tokenUsage、evaluation、optimization 等公共信息，
 * 业务层只需关注泛型 {@code T} 代表的业务数据。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 业务层只关心 data（如 List<TagInfo>）
 * AgentResponse<List<TagInfo>> response = agentExecutor.execute("tagger", input, ctx,
 *     taskResult -> parseTags(taskResult));
 *
 * // 框架自动填充公共字段
 * response.data();         // List<TagInfo> — 业务数据
 * response.trace();        // ExecutionTrace — 自动采集
 * response.tokenUsage();   // TokenUsage — 自动采集
 * response.evaluation();   // EvaluationResult — 自动查询
 * response.optimization(); // OptimizationReport — 自动查询
 * }</pre>
 *
 * @param <T>          业务数据类型
 * @param data         业务数据（由业务逻辑回调填充）
 * @param tokenUsage   Token 消耗统计（框架自动采集）
 * @param trace        执行链路追踪（框架自动采集）
 * @param processTime  处理耗时 ms（框架自动采集）
 * @param evaluation   评测结果（框架自动查询，未启用评测时为 null）
 * @param optimization 优化建议报告（分数低于阈值时非 null）
 * @param error        错误信息（成功时为 null）
 */
public record AgentResponse<T>(
        T data,
        TokenUsage tokenUsage,
        ExecutionTrace trace,
        long processTime,
        EvaluationResult evaluation,
        OptimizationReport optimization,
        String error
) {

    /**
     * 是否执行成功（无错误）。
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * 快速创建成功响应。
     *
     * @param data        业务数据
     * @param tokenUsage  Token 消耗
     * @param trace       执行链路
     * @param processTime 处理耗时
     * @return 成功的 AgentResponse
     */
    public static <T> AgentResponse<T> success(T data, TokenUsage tokenUsage,
                                                ExecutionTrace trace, long processTime) {
        return new AgentResponse<>(data, tokenUsage, trace, processTime, null, null, null);
    }

    /**
     * 快速创建失败响应。
     *
     * @param error       错误信息
     * @param trace       执行链路（可能包含部分步骤）
     * @param processTime 处理耗时
     * @return 失败的 AgentResponse
     */
    public static <T> AgentResponse<T> failure(String error, ExecutionTrace trace, long processTime) {
        return new AgentResponse<>(null, null, trace, processTime, null, null, error);
    }

    /**
     * 追加评测结果，返回新的 AgentResponse。
     */
    public AgentResponse<T> withEvaluation(EvaluationResult evaluation) {
        return new AgentResponse<>(data, tokenUsage, trace, processTime, evaluation, optimization, error);
    }

    /**
     * 追加优化报告，返回新的 AgentResponse。
     */
    public AgentResponse<T> withOptimization(OptimizationReport optimization) {
        return new AgentResponse<>(data, tokenUsage, trace, processTime, evaluation, optimization, error);
    }
}
