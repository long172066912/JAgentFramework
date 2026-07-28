package com.jrl.ai.agent.core.evaluation;

import java.util.List;

/**
 * 评测结果存储 — 持久化评测数据。
 *
 * <p>框架默认实现为 JSON 文件存储，用户可实现此接口替换为数据库等。
 */
public interface EvaluationStore {

    /**
     * 保存评测结果。
     *
     * @param result 评测结果
     */
    void save(EvaluationResult result);

    /**
     * 按 Agent 查询评测历史（按时间倒序）。
     *
     * @param agentId Agent 标识
     * @param limit   最大返回数量
     * @return 评测结果列表
     */
    List<EvaluationResult> findByAgent(String agentId, int limit);

    /**
     * 按会话查询评测历史。
     *
     * @param sessionId 会话 ID
     * @return 评测结果列表
     */
    List<EvaluationResult> findBySession(String sessionId);

    /**
     * 获取聚合指标。
     *
     * @param agentId    Agent 标识
     * @param dimension  评测维度
     * @param windowMs   时间窗口（ms），0 表示全量
     * @return 聚合指标
     */
    EvaluationAggregate getAggregate(String agentId, EvaluationDimension dimension, long windowMs);
}
