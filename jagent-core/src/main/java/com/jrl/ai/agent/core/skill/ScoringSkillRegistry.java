package com.jrl.ai.agent.core.skill;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 评分感知的 Skill 注册表 — 包装 {@link SkillRegistry} 与 {@link SkillScorer}。
 *
 * <p>在标准注册表的注册/查找能力基础上，增加按评分选择 Skill 的能力：
 * <ul>
 *   <li>{@link #getBest(String)} — 获取指定 Agent 评分最高的可用 Skill</li>
 *   <li>{@link #rank(String)} — 获取按评分降序排列的所有可用 Skill</li>
 * </ul>
 *
 * <p>注册/查找/注销操作委托给内部的 {@link SkillRegistry}，
 * 评分逻辑委托给 {@link SkillScorer}。
 *
 * @see SkillRegistry
 * @see SkillScorer
 */
public class ScoringSkillRegistry implements SkillRegistry {

    private final SkillRegistry delegate;
    private final SkillScorer scorer;

    /**
     * 创建评分感知的 Skill 注册表。
     *
     * @param delegate 被包装的基础注册表
     * @param scorer   评分器
     */
    public ScoringSkillRegistry(SkillRegistry delegate, SkillScorer scorer) {
        this.delegate = delegate;
        this.scorer = scorer;
    }

    @Override
    public void register(Skill skill) {
        delegate.register(skill);
    }

    @Override
    public Optional<Skill> get(String name) {
        return delegate.get(name);
    }

    @Override
    public Collection<Skill> all() {
        return delegate.all();
    }

    @Override
    public void unregister(String name) {
        delegate.unregister(name);
    }

    /**
     * 获取指定 Agent 评分最高的可用 Skill。
     *
     * <p>仅考虑 {@link Skill#isAvailable()} 返回 {@code true} 的 Skill。
     *
     * @param agentId Agent 标识
     * @return 评分最高的可用 Skill，无可用 Skill 时返回 {@link Optional#empty()}
     */
    public Optional<Skill> getBest(String agentId) {
        List<Skill> ranked = rank(agentId);
        return ranked.isEmpty() ? Optional.empty() : Optional.of(ranked.get(0));
    }

    /**
     * 获取按评分降序排列的所有可用 Skill。
     *
     * <p>仅包含 {@link Skill#isAvailable()} 返回 {@code true} 的 Skill。
     *
     * @param agentId Agent 标识
     * @return 按评分降序排列的可用 Skill 列表
     */
    public List<Skill> rank(String agentId) {
        List<Skill> available = all().stream()
                .filter(Skill::isAvailable)
                .toList();
        return scorer.rank(agentId, available);
    }

    /**
     * 获取被包装的基础注册表。
     *
     * @return 委托的 SkillRegistry
     */
    public SkillRegistry getDelegate() {
        return delegate;
    }

    /**
     * 获取评分器。
     *
     * @return SkillScorer 实例
     */
    public SkillScorer getScorer() {
        return scorer;
    }
}
