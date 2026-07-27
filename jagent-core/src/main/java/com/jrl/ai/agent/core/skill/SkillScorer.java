package com.jrl.ai.agent.core.skill;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Skill 评分器 — 按 Agent 维度对 Skill 进行评分与排序。
 *
 * <p>不同 Agent 对同一 Skill 的评分可以不同，评分综合了：
 * <ul>
 *   <li>外部配置的静态优先级（如 YAML 配置）</li>
 *   <li>运行时动态执行统计（成功率）</li>
 * </ul>
 *
 * <p>通过 {@link #rank(String, Collection)} 可按评分降序选择最合适的 Skill。
 *
 * @see Skill
 * @see SkillRegistry
 */
public interface SkillScorer {

    /**
     * 获取指定 Agent 对某 Skill 的评分。
     *
     * <p>评分范围通常为 0.0 ~ 1.0，值越高表示该 Skill 对该 Agent 越合适。
     *
     * @param agentId Agent 标识
     * @param skill   待评分的技能
     * @return 评分值
     */
    double score(String agentId, Skill skill);

    /**
     * 按评分降序排列 Skill 列表。
     *
     * <p>默认实现按 {@link #score(String, Skill)} 降序排序。
     *
     * @param agentId Agent 标识
     * @param skills  待排序的技能集合
     * @return 按评分降序排列的技能列表
     */
    default List<Skill> rank(String agentId, Collection<Skill> skills) {
        return skills.stream()
                .sorted(Comparator.comparingDouble((Skill s) -> score(agentId, s)).reversed())
                .toList();
    }

    /**
     * 记录一次 Skill 执行结果，用于更新动态评分。
     *
     * @param agentId   Agent 标识
     * @param skillName 技能名称
     * @param success   是否执行成功
     */
    void recordExecution(String agentId, String skillName, boolean success);
}
