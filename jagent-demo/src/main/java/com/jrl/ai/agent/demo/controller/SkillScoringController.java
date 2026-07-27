package com.jrl.ai.agent.demo.controller;

import com.jrl.ai.agent.agentscope.skill.SkillScoringInterceptor;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import com.jrl.ai.agent.core.context.AgentContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * Skill 评分演示端点 — 展示评分拦截器的前置/后置执行和评分结果。
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>模拟 Skill 执行（触发 before/after 拦截器）</li>
 *   <li>查看各 Agent 对 Skill 的评分</li>
 *   <li>按评分排序 Skill 列表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/skill-scoring")
public class SkillScoringController {

    private final SkillScoringInterceptor scoringInterceptor;

    public SkillScoringController(SkillScoringInterceptor scoringInterceptor) {
        this.scoringInterceptor = scoringInterceptor;
    }

    /**
     * 模拟 Skill 执行 — 触发 before/after 拦截器并记录统计。
     *
     * @param request 模拟执行请求（agentId + skillName + success）
     * @return 执行结果与当前评分
     */
    @PostMapping("/simulate")
    public Mono<Map<String, Object>> simulate(@RequestBody SimulateRequest request) {
        return Mono.fromCallable(() -> {
            String agentId = request.agentId();
            String skillName = request.skillName();
            boolean success = request.success() == null || request.success();

            // 构造模拟 Skill 和 SkillContext
            Skill mockSkill = createMockSkill(skillName);
            SkillContext context = createMockContext(agentId, skillName);

            // 前置通知
            scoringInterceptor.beforeExecute(mockSkill, context);

            // 模拟执行结果
            SkillResult result = success
                    ? SkillResult.success(skillName, "模拟执行成功", 10)
                    : SkillResult.failure(skillName, "模拟执行失败", 10);

            // 后置通知（或异常通知）
            if (success) {
                scoringInterceptor.afterExecute(mockSkill, context, result);
            } else {
                scoringInterceptor.onError(mockSkill, context, new RuntimeException("模拟失败"));
            }

            // 获取当前评分
            double currentScore = scoringInterceptor.score(agentId, mockSkill);
            int[] stats = scoringInterceptor.getStats(agentId, skillName);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("agentId", agentId);
            response.put("skillName", skillName);
            response.put("success", success);
            response.put("currentScore", currentScore);
            response.put("stats", stats != null
                    ? Map.of("successCount", stats[0], "totalCount", stats[1])
                    : Map.of("message", "无执行统计"));

            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查看指定 Agent 对所有 Skill 的评分。
     *
     * @param agentId Agent 标识
     * @return 各 Skill 的评分详情
     */
    @GetMapping("/scores/{agentId}")
    public Mono<Map<String, Object>> getScores(@PathVariable String agentId) {
        return Mono.fromCallable(() -> {
            // 模拟几个 Skill 进行评分展示
            List<Map<String, Object>> skillScores = new ArrayList<>();

            for (String skillName : List.of("vector_search", "vector_upsert", "vector_get")) {
                Skill skill = createMockSkill(skillName);
                double score = scoringInterceptor.score(agentId, skill);
                int[] stats = scoringInterceptor.getStats(agentId, skillName);

                Map<String, Object> skillInfo = new LinkedHashMap<>();
                skillInfo.put("skillName", skillName);
                skillInfo.put("score", score);
                skillInfo.put("stats", stats != null
                        ? Map.of("successCount", stats[0], "totalCount", stats[1],
                                 "successRate", stats[1] > 0 ? (double) stats[0] / stats[1] : 0)
                        : Map.of("message", "无执行统计"));
                skillScores.add(skillInfo);
            }

            // 按评分降序排序
            skillScores.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("agentId", agentId);
            response.put("rankedSkills", skillScores);

            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 批量模拟执行 — 模拟多次 Skill 执行后查看评分变化。
     *
     * @param requests 批量模拟请求
     * @return 每次执行的结果汇总
     */
    @PostMapping("/simulate/batch")
    public Mono<List<Map<String, Object>>> simulateBatch(@RequestBody List<SimulateRequest> requests) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> results = new ArrayList<>();
            for (SimulateRequest request : requests) {
                String agentId = request.agentId();
                String skillName = request.skillName();
                boolean success = request.success() == null || request.success();

                Skill mockSkill = createMockSkill(skillName);
                SkillContext context = createMockContext(agentId, skillName);

                scoringInterceptor.beforeExecute(mockSkill, context);
                SkillResult result = success
                        ? SkillResult.success(skillName, "模拟成功", 5)
                        : SkillResult.failure(skillName, "模拟失败", 5);

                if (success) {
                    scoringInterceptor.afterExecute(mockSkill, context, result);
                } else {
                    scoringInterceptor.onError(mockSkill, context, new RuntimeException("模拟失败"));
                }

                double score = scoringInterceptor.score(agentId, mockSkill);
                int[] stats = scoringInterceptor.getStats(agentId, skillName);

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("agentId", agentId);
                r.put("skillName", skillName);
                r.put("success", success);
                r.put("score", score);
                r.put("stats", stats != null ? Map.of("success", stats[0], "total", stats[1]) : Map.of());
                results.add(r);
            }
            return results;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ===== 内部方法 =====

    private Skill createMockSkill(String name) {
        return new Skill() {
            @Override public String name() { return name; }
            @Override public String description() { return "模拟 Skill: " + name; }
            @Override public SkillResult execute(SkillContext context) {
                return SkillResult.success(name, "ok", 0);
            }
        };
    }

    private SkillContext createMockContext(String agentId, String skillName) {
        AgentContext agentContext = AgentContext.builder()
                .sessionId("demo-session")
                .userId("demo-user")
                .build();
        agentContext.put("agentId", agentId);
        return new SkillContext(skillName, "模拟输入", agentContext, Map.of());
    }

    /**
     * 模拟执行请求。
     */
    public record SimulateRequest(
            /** Agent 标识 */
            String agentId,
            /** Skill 名称 */
            String skillName,
            /** 是否模拟成功（默认 true） */
            Boolean success
    ) {}
}
