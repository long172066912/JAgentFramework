package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.plan.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Planner 规划流程测试 — 验证 GOAP 风格规划能力。
 */
@DisplayName("Planner 规划流程测试")
class PlannerTest {

    private AgentContext context;
    private Goal translateGoal;

    @BeforeEach
    void setUp() {
        context = AgentContext.builder()
                .sessionId("session-001")
                .userId("user-alice")
                .build();

        translateGoal = new Goal() {
            @Override public String name() { return "translate-doc"; }
            @Override public String description() { return "将文档翻译为目标语言"; }
            @Override public boolean isAchieved(Object state) {
                return state instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("translated"));
            }
            @Override public int priority() { return 10; }
        };
    }

    @Test
    @DisplayName("Goal 创建：验证目标属性")
    void testGoalCreation() {
        assertEquals("translate-doc", translateGoal.name());
        assertEquals(10, translateGoal.priority());
        assertFalse(translateGoal.isAchieved(Map.of()));
        assertTrue(translateGoal.isAchieved(Map.of("translated", true)));
    }

    @Test
    @DisplayName("PlanStep 创建：验证步骤属性与前置/后置条件")
    void testPlanStepCreation() {
        PlanStep step = PlanStep.pending("提取文档内容", 0, "文档存在", "文档内容已提取");

        assertNotNull(step.id());
        assertEquals("提取文档内容", step.description());
        assertEquals(0, step.order());
        assertEquals("文档存在", step.precondition());
        assertEquals("文档内容已提取", step.postcondition());
        assertEquals(PlanStep.StepStatus.PENDING, step.status());
    }

    @Test
    @DisplayName("Plan 创建：验证计划结构")
    void testPlanCreation() {
        List<PlanStep> steps = List.of(
                PlanStep.pending("提取文档", 0, "文档存在", "内容已提取"),
                PlanStep.pending("翻译内容", 1, "内容已提取", "内容已翻译"),
                PlanStep.pending("格式化输出", 2, "内容已翻译", "翻译完成")
        );

        Plan plan = Plan.create(translateGoal, steps);

        assertNotNull(plan.id());
        assertEquals(translateGoal, plan.goal());
        assertEquals(3, plan.steps().size());
        assertEquals(PlanStatus.CREATED, plan.status());
    }

    @Test
    @DisplayName("Planner 规划：Mock Planner 生成计划")
    void testPlannerPlan() {
        // Mock Planner 实现
        Planner planner = new Planner() {
            @Override
            public Optional<Plan> plan(Goal goal, AgentContext ctx, Object state) {
                List<PlanStep> steps = List.of(
                        PlanStep.pending("分析输入", 0, "有输入", "已分析"),
                        PlanStep.pending("执行翻译", 1, "已分析", "已翻译")
                );
                return Optional.of(Plan.create(goal, steps));
            }

            @Override
            public boolean needsReplan(Plan currentPlan, Object state) {
                return state instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"));
            }
        };

        Optional<Plan> plan = planner.plan(translateGoal, context, Map.of());
        assertTrue(plan.isPresent());
        assertEquals(2, plan.get().steps().size());
        assertEquals("分析输入", plan.get().steps().get(0).description());
    }

    @Test
    @DisplayName("重规划评估：检测是否需要重新规划")
    void testNeedsReplan() {
        Planner planner = new Planner() {
            @Override
            public Optional<Plan> plan(Goal goal, AgentContext ctx, Object state) {
                return Optional.of(Plan.create(goal, List.of()));
            }
            @Override
            public boolean needsReplan(Plan currentPlan, Object state) {
                return state instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"));
            }
        };

        Plan plan = Plan.create(translateGoal, List.of());

        // 正常状态不需要重规划
        assertFalse(planner.needsReplan(plan, Map.of("status", "ok")));
        // 错误状态需要重规划
        assertTrue(planner.needsReplan(plan, Map.of("error", true)));
    }

    @Test
    @DisplayName("PlanStatus 状态流转")
    void testPlanStatusValues() {
        assertEquals(7, PlanStatus.values().length);
        assertNotNull(PlanStatus.NEEDS_REPLAN);
        assertNotNull(PlanStatus.CREATED);
        assertNotNull(PlanStatus.EXECUTING);
    }

    @Test
    @DisplayName("PlanStep.StepStatus 状态枚举")
    void testStepStatusValues() {
        assertEquals(5, PlanStep.StepStatus.values().length);
        assertNotNull(PlanStep.StepStatus.SUCCEEDED);
        assertNotNull(PlanStep.StepStatus.SKIPPED);
    }
}
