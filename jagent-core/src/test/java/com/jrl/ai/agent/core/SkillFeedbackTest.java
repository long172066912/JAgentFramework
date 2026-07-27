package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.feedback.*;
import com.jrl.ai.agent.core.skill.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skill + Feedback 流程测试 — 验证技能执行与反馈采集链路。
 */
@DisplayName("Skill + Feedback 流程测试")
class SkillFeedbackTest {

    private Skill testSkill;
    private SkillContext skillContext;
    private List<Feedback> collectedFeedbacks;

    @BeforeEach
    void setUp() {
        // 创建测试技能
        testSkill = new Skill() {
            @Override public String name() { return "translate"; }
            @Override public String description() { return "翻译技能"; }
            @Override
            public SkillResult execute(SkillContext context) {
                long start = System.currentTimeMillis();
                String output = "[翻译] " + context.input();
                return SkillResult.success(name(), output, System.currentTimeMillis() - start);
            }
        };

        // 创建技能上下文
        AgentContext agentContext = AgentContext.builder()
                .sessionId("session-001")
                .userId("user-alice")
                .build();
        skillContext = new SkillContext("translate", "Hello World", agentContext, Map.of());

        // 反馈收集器
        collectedFeedbacks = new ArrayList<>();
    }

    @Test
    @DisplayName("Skill 执行：正常执行并返回结果")
    void testSkillExecution() {
        SkillResult result = testSkill.execute(skillContext);

        assertTrue(result.success());
        assertEquals("translate", result.skillName());
        assertEquals("[翻译] Hello World", result.output());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    @DisplayName("Skill 注册表：注册、查找、列举")
    void testSkillRegistry() {
        Map<String, Skill> store = new HashMap<>();
        SkillRegistry registry = new SkillRegistry() {
            @Override public void register(Skill s) { store.put(s.name(), s); }
            @Override public Optional<Skill> get(String name) { return Optional.ofNullable(store.get(name)); }
            @Override public Collection<Skill> all() { return List.copyOf(store.values()); }
            @Override public void unregister(String name) { store.remove(name); }
        };

        registry.register(testSkill);
        assertTrue(registry.get("translate").isPresent());
        assertEquals(1, registry.all().size());
    }

    @Test
    @DisplayName("Skill 拦截器：执行前后拦截")
    void testSkillInterceptor() {
        List<String> events = new ArrayList<>();

        SkillInterceptor interceptor = new SkillInterceptor() {
            @Override
            public void beforeExecute(Skill skill, SkillContext ctx) {
                events.add("before:" + skill.name());
            }
            @Override
            public void afterExecute(Skill skill, SkillContext ctx, SkillResult result) {
                events.add("after:" + result.success());
            }
        };

        // 模拟拦截执行
        interceptor.beforeExecute(testSkill, skillContext);
        SkillResult result = testSkill.execute(skillContext);
        interceptor.afterExecute(testSkill, skillContext, result);

        assertEquals(2, events.size());
        assertEquals("before:translate", events.get(0));
        assertEquals("after:true", events.get(1));
    }

    @Test
    @DisplayName("Feedback 创建：显式反馈")
    void testExplicitFeedback() {
        Feedback feedback = Feedback.of(
                "agent-001", "session-001",
                FeedbackTarget.SKILL, FeedbackType.EXPLICIT, 0.9
        );

        assertNotNull(feedback.id());
        assertEquals("agent-001", feedback.agentId());
        assertEquals(FeedbackTarget.SKILL, feedback.target());
        assertEquals(0.9, feedback.score());
    }

    @Test
    @DisplayName("Feedback 处理：PromptFeedbackHandler 处理提示词反馈")
    void testPromptFeedbackHandler() {
        PromptFeedbackHandler handler = new PromptFeedbackHandler() {
            @Override
            public void handle(Feedback feedback) {
                collectedFeedbacks.add(feedback);
            }
            @Override
            public String optimizePrompt(String currentPrompt, Feedback feedback) {
                return currentPrompt + " [优化:" + feedback.score() + "]";
            }
        };

        assertTrue(handler.accepts(FeedbackTarget.PROMPT));
        assertFalse(handler.accepts(FeedbackTarget.SKILL));

        Feedback feedback = Feedback.of("agent-001", "session-001",
                FeedbackTarget.PROMPT, FeedbackType.EXPLICIT, 0.8);
        handler.handle(feedback);

        assertEquals(1, collectedFeedbacks.size());
        String optimized = handler.optimizePrompt("原始提示词", feedback);
        assertTrue(optimized.contains("[优化:0.8]"));
    }

    @Test
    @DisplayName("Feedback 处理：SkillFeedbackHandler 处理技能反馈")
    void testSkillFeedbackHandler() {
        Map<String, Double> skillScores = new HashMap<>();

        SkillFeedbackHandler handler = new SkillFeedbackHandler() {
            @Override
            public void handle(Feedback feedback) {
                collectedFeedbacks.add(feedback);
            }
            @Override
            public void recordSkillFeedback(String skillName, Feedback feedback) {
                skillScores.merge(skillName, feedback.score(), Double::sum);
            }
            @Override
            public double getSkillScore(String skillName) {
                return skillScores.getOrDefault(skillName, 0.0);
            }
        };

        assertTrue(handler.accepts(FeedbackTarget.SKILL));

        Feedback fb1 = Feedback.of("agent-001", "session-001",
                FeedbackTarget.SKILL, FeedbackType.EXPLICIT, 0.9);
        Feedback fb2 = Feedback.of("agent-001", "session-001",
                FeedbackTarget.SKILL, FeedbackType.IMPLICIT, 0.7);

        handler.recordSkillFeedback("translate", fb1);
        handler.recordSkillFeedback("translate", fb2);

        assertEquals(1.6, handler.getSkillScore("translate"), 0.001);
    }
}
