package com.jrl.ai.agent.agentscope.skill;

import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import com.jrl.ai.agent.core.skill.SkillScorer;
import com.jrl.ai.agent.core.skill.SkillInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 评分拦截器 — 通过前置/后置钩子采集执行统计，结合配置优先级计算评分。
 *
 * <p>同时实现 {@link SkillInterceptor} 和 {@link SkillScorer} 接口：
 * <ul>
 *   <li>作为拦截器：在 before/after/onError 中自动采集 (agentId, skillName) 维度的执行统计</li>
 *   <li>作为评分器：综合静态配置优先级和动态成功率计算最终评分</li>
 * </ul>
 *
 * <p>评分算法：{@code configuredPriority * 0.4 + successRate * 0.6}。
 * 基础分来源：优先取配置侧 skill-priorities 覆盖值，未配置时使用
 * Skill 自描述的 {@link Skill#priority()}（默认 0.5）。
 */
public class SkillScoringInterceptor implements SkillInterceptor, SkillScorer {

    private static final Logger log = LoggerFactory.getLogger(SkillScoringInterceptor.class);

    /** 静态配置优先级（运行时覆盖）：agentId -> (skillName -> baseScore) */
    private final Map<String, Map<String, Double>> configuredPriorities;

    /** 动态执行统计：agentId -> (skillName -> [successCount, totalCount]) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, int[]>> stats =
            new ConcurrentHashMap<>();

    /** 默认基础分（Skill 未自描述 priority 时使用） */
    private static final double DEFAULT_BASE_SCORE = 0.5;

    /** 静态配置权重 */
    private static final double CONFIG_WEIGHT = 0.4;

    /** 动态统计权重 */
    private static final double STATS_WEIGHT = 0.6;

    /**
     * 创建评分拦截器。
     *
     * @param configuredPriorities 静态配置优先级（agentId -> skillName -> 基础分）
     */
    public SkillScoringInterceptor(Map<String, Map<String, Double>> configuredPriorities) {
        this.configuredPriorities = configuredPriorities != null
                ? new ConcurrentHashMap<>(configuredPriorities) : new ConcurrentHashMap<>();
    }

    // ===== SkillInterceptor =====

    @Override
    public void beforeExecute(Skill skill, SkillContext context) {
        String agentId = extractAgentId(context);
        log.debug("Skill 执行开始: agent={}, skill={}", agentId, skill.name());
    }

    @Override
    public void afterExecute(Skill skill, SkillContext context, SkillResult result) {
        String agentId = extractAgentId(context);
        recordExecution(agentId, skill.name(), result.success());
        log.debug("Skill 执行完成: agent={}, skill={}, success={}", agentId, skill.name(), result.success());
    }

    @Override
    public void onError(Skill skill, SkillContext context, Throwable error) {
        String agentId = extractAgentId(context);
        recordExecution(agentId, skill.name(), false);
        log.warn("Skill 执行异常: agent={}, skill={}, error={}", agentId, skill.name(), error.getMessage());
    }

    // ===== SkillScorer =====

    @Override
    public double score(String agentId, Skill skill) {
        double configScore = getConfiguredScore(agentId, skill);
        double statsScore = getStatsScore(agentId, skill.name());

        if (statsScore < 0) {
            // 无执行统计，仅使用配置分
            return configScore;
        }

        return CONFIG_WEIGHT * configScore + STATS_WEIGHT * statsScore;
    }

    @Override
    public void recordExecution(String agentId, String skillName, boolean success) {
        stats.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>())
                .compute(skillName, (k, counts) -> {
                    if (counts == null) {
                        return new int[]{success ? 1 : 0, 1};
                    }
                    if (success) counts[0]++;
                    counts[1]++;
                    return counts;
                });
    }

    /**
     * 获取指定 (agentId, skillName) 的执行统计。
     *
     * @param agentId   Agent 标识
     * @param skillName 技能名称
     * @return [successCount, totalCount]，无统计时返回 null
     */
    public int[] getStats(String agentId, String skillName) {
        ConcurrentHashMap<String, int[]> agentStats = stats.get(agentId);
        return agentStats != null ? agentStats.get(skillName) : null;
    }

    // ===== 内部方法 =====

    /**
     * 获取基础分：配置侧覆盖值优先，未配置时回退到 Skill 自描述 priority()。
     */
    private double getConfiguredScore(String agentId, Skill skill) {
        Map<String, Double> agentPriorities = configuredPriorities.get(agentId);
        if (agentPriorities != null) {
            Double priority = agentPriorities.get(skill.name());
            if (priority != null) {
                return priority;
            }
        }
        // 基础分是 Skill 的固有能力声明，配置仅作运行时覆盖
        double selfPriority = skill.priority();
        return selfPriority > 0 ? selfPriority : DEFAULT_BASE_SCORE;
    }

    private double getStatsScore(String agentId, String skillName) {
        int[] counts = getStats(agentId, skillName);
        if (counts == null || counts[1] == 0) {
            return -1; // 无统计
        }
        return (double) counts[0] / counts[1];
    }

    private String extractAgentId(SkillContext context) {
        if (context.agentContext() != null) {
            return context.agentContext().<String>get("agentId").orElse("unknown");
        }
        return "unknown";
    }
}
