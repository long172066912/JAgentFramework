package com.jrl.ai.agent.core.evaluation;

import java.util.List;

/**
 * 优化报告存储 — 持久化优化分析结果。
 *
 * <p>框架默认实现为 JSON 文件存储，用户可实现此接口替换为数据库等。
 */
public interface OptimizationReportStore {

    /**
     * 保存优化报告。
     *
     * @param report 优化报告
     */
    void save(OptimizationReport report);

    /**
     * 按 Agent 查询优化报告历史（按时间倒序）。
     *
     * @param agentId Agent 标识
     * @param limit   最大返回数量
     * @return 优化报告列表
     */
    List<OptimizationReport> findByAgent(String agentId, int limit);

    /**
     * 获取最新的优化报告。
     *
     * @param agentId Agent 标识
     * @return 最新的优化报告，不存在时返回 null
     */
    OptimizationReport findLatest(String agentId);
}
