package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.feedback.*;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.mock.MockAgent;
import com.jrl.ai.agent.core.mock.MockModel;
import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.model.ModelRegistry;
import com.jrl.ai.agent.core.plan.*;
import com.jrl.ai.agent.core.prompt.PromptBuilder;
import com.jrl.ai.agent.core.router.Router;
import com.jrl.ai.agent.core.skill.*;
import com.jrl.ai.agent.core.task.Task;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.TaskStatus;
import com.jrl.ai.agent.core.task.contract.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端全链路集成测试 — 模拟完整业务流程。
 *
 * <p>链路：TaskRequest → Task → Router → Agent(Model) → Skill → Feedback → Planner → TaskResult → TaskResponse
 */
@DisplayName("端到端全链路集成测试")
class EndToEndFlowTest {

    // ========== 基础设施 ==========
    private MockModel qwenModel;
    private MockModel gptModel;
    private Map<String, Model> modelStore;
    private ModelRegistry modelRegistry;

    private MockAgent translateAgent;
    private MockAgent summaryAgent;
    private Map<String, Agent> agentStore;
    private AgentRegistry agentRegistry;
    private Router router;

    private Map<String, Skill> skillStore;
    private SkillRegistry skillRegistry;
    private List<Feedback> feedbackLog;

    private Planner planner;

    @BeforeEach
    void setUp() {
        // 1. 模型层 — 多模型注册
        qwenModel = new MockModel("qwen-max", "dashscope")
                .withFixedResponse("这是一段翻译后的中文内容。");
        gptModel = new MockModel("gpt-4.1", "openai")
                .withFixedResponse("This is a summary of the document.");

        modelStore = new HashMap<>();
        modelRegistry = new ModelRegistry() {
            private Model defaultModel;
            @Override public void register(Model m) { modelStore.put(m.modelId(), m); if (defaultModel == null) defaultModel = m; }
            @Override public Optional<Model> resolve(String ref) {
                String id = ref.contains(":") ? ref.split(":")[1] : ref;
                return Optional.ofNullable(modelStore.get(id));
            }
            @Override public Optional<Model> defaultModel() { return Optional.ofNullable(defaultModel); }
            @Override public Collection<Model> all() { return List.copyOf(modelStore.values()); }
        };
        modelRegistry.register(qwenModel);
        modelRegistry.register(gptModel);

        // 2. Agent 层 — 不同 Agent 使用不同模型
        translateAgent = new MockAgent("agent-translate", "翻译助手", qwenModel);
        summaryAgent = new MockAgent("agent-summary", "摘要助手", gptModel);

        agentStore = new HashMap<>();
        agentRegistry = new AgentRegistry() {
            @Override public void register(Agent a) { agentStore.put(a.id(), a); }
            @Override public Optional<Agent> get(String id) { return Optional.ofNullable(agentStore.get(id)); }
            @Override public void unregister(String id) { agentStore.remove(id); }
            @Override public Collection<Agent> all() { return List.copyOf(agentStore.values()); }
        };
        agentRegistry.register(translateAgent);
        agentRegistry.register(summaryAgent);

        // 3. 路由器 — 按任务类型路由到不同 Agent
        router = new Router() {
            @Override
            public Agent route(Task task, AgentContext context) {
                String type = task.type();
                return switch (type) {
                    case "translate" -> translateAgent;
                    case "summary" -> summaryAgent;
                    default -> translateAgent;
                };
            }
            @Override public String name() { return "type-based-router"; }
        };

        // 4. Skill 层 — 注册可用技能
        skillStore = new HashMap<>();
        skillRegistry = new SkillRegistry() {
            @Override public void register(Skill s) { skillStore.put(s.name(), s); }
            @Override public Optional<Skill> get(String name) { return Optional.ofNullable(skillStore.get(name)); }
            @Override public Collection<Skill> all() { return List.copyOf(skillStore.values()); }
            @Override public void unregister(String name) { skillStore.remove(name); }
        };
        skillRegistry.register(new Skill() {
            @Override public String name() { return "text-cleaner"; }
            @Override public String description() { return "清洗文本"; }
            @Override public SkillResult execute(SkillContext context) {
                return SkillResult.success(name(), "[cleaned] " + context.input(), 5);
            }
        });

        // 5. 反馈层
        feedbackLog = new ArrayList<>();

        // 6. 规划层
        planner = new Planner() {
            @Override
            public Optional<Plan> plan(Goal goal, AgentContext ctx, Object state) {
                List<PlanStep> steps = List.of(
                        PlanStep.pending("清洗输入文本", 0, "原始文本存在", "文本已清洗"),
                        PlanStep.pending("调用翻译 Agent", 1, "文本已清洗", "翻译完成"),
                        PlanStep.pending("格式化输出", 2, "翻译完成", "最终结果就绪")
                );
                return Optional.of(Plan.create(goal, steps));
            }
            @Override
            public boolean needsReplan(Plan currentPlan, Object state) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("完整链路：翻译任务从请求到响应")
    void testFullTranslateFlow() {
        // === Step 1: 构建 TaskRequest（模拟传输层输入） ===
        TaskRequest request = TaskRequest.builder()
                .taskId("task-e2e-001")
                .taskType("translate")
                .sessionId("session-e2e")
                .userId("user-bob")
                .priority(TaskRequest.PRIORITY_URGENT)
                .modelId("qwen-max")
                .promptTemplate("translate-prompt-v1")
                .promptVariables(Map.of("targetLang", "中文"))
                .skillNames(List.of("text-cleaner"))
                .payload(Map.of("text", "Hello, this is a test document."))
                .timeoutMs(60000)
                .build();

        assertNotNull(request.taskId());
        assertEquals("translate", request.taskType());

        // === Step 2: TaskRequest → Task（协议转换） ===
        Task task = TaskContractConverter.toTask(request);
        assertEquals(TaskStatus.PENDING, task.status());
        assertEquals("task-e2e-001", task.id());

        // === Step 3: TaskRequest → AgentContext ===
        AgentContext context = TaskContractConverter.toContext(request);
        assertEquals("session-e2e", context.sessionId());
        assertEquals("user-bob", context.userId());
        assertEquals("qwen-max", context.<String>get("modelId").orElse(null));

        // === Step 4: 路由到目标 Agent ===
        Agent selectedAgent = router.route(task, context);
        assertNotNull(selectedAgent);
        assertEquals("agent-translate", selectedAgent.id());
        assertEquals("翻译助手", selectedAgent.name());

        // === Step 5: 规划执行计划 ===
        Goal goal = new Goal() {
            @Override public String name() { return "translate-text"; }
            @Override public String description() { return "将英文文本翻译为中文"; }
            @Override public boolean isAchieved(Object state) { return false; }
        };
        Optional<Plan> plan = planner.plan(goal, context, Map.of());
        assertTrue(plan.isPresent());
        assertEquals(3, plan.get().steps().size());
        assertEquals("清洗输入文本", plan.get().steps().get(0).description());

        // === Step 6: 执行 Skill（文本清洗） ===
        Optional<Skill> cleanerSkill = skillRegistry.get("text-cleaner");
        assertTrue(cleanerSkill.isPresent());

        SkillContext skillCtx = new SkillContext(
                "text-cleaner",
                (String) request.payload().get("text"),
                context,
                Map.of()
        );
        SkillResult skillResult = cleanerSkill.get().execute(skillCtx);
        assertTrue(skillResult.success());
        assertTrue(skillResult.output().contains("[cleaned]"));

        // === Step 7: Agent 执行（调用模型） ===
        task = task.start();
        assertEquals(TaskStatus.RUNNING, task.status());

        ChatMessage input = ChatMessage.user(skillResult.output());
        TaskResult taskResult = selectedAgent.execute(input, context);

        assertTrue(taskResult.isSuccess());
        assertEquals("task-agent-translate", taskResult.taskId());
        assertNotNull(taskResult.usage());
        assertEquals("qwen-max", taskResult.usage().modelId());

        // === Step 8: 采集反馈 ===
        Feedback feedback = Feedback.of(
                selectedAgent.id(), context.sessionId(),
                FeedbackTarget.SKILL, FeedbackType.IMPLICIT,
                skillResult.success() ? 1.0 : 0.0
        );
        feedbackLog.add(feedback);
        assertEquals(1, feedbackLog.size());
        assertEquals(1.0, feedbackLog.get(0).score());

        // === Step 9: 完成任务 ===
        task = task.complete();
        assertEquals(TaskStatus.COMPLETED, task.status());
        assertTrue(task.durationMs() >= 0);

        // === Step 10: TaskResult → TaskResponse（输出契约） ===
        TaskResponse response = taskResult.toResponse();
        assertNotNull(response);
        assertEquals("task-agent-translate", response.taskId());
        assertEquals(com.jrl.ai.agent.core.task.contract.ResponseStatus.SUCCESS, response.status());
        assertEquals(100, response.progress());
        assertNotNull(response.result());
        assertTrue(response.processTime() >= 0);
        assertNotNull(response.timestamp());

        // === 验证：模型调用次数 ===
        assertEquals(1, qwenModel.getCallCount());
        assertEquals(0, gptModel.getCallCount()); // 摘要模型未被调用
    }

    @Test
    @DisplayName("完整链路：摘要任务路由到不同 Agent 和模型")
    void testFullSummaryFlow() {
        // 构建摘要任务
        TaskRequest request = TaskRequest.builder()
                .taskId("task-e2e-002")
                .taskType("summary")
                .sessionId("session-e2e")
                .userId("user-bob")
                .modelId("gpt-4.1")
                .payload(Map.of("text", "这是一篇很长的文章..."))
                .build();

        Task task = TaskContractConverter.toTask(request);
        AgentContext context = TaskContractConverter.toContext(request);

        // 路由 — 应该选择摘要 Agent
        Agent agent = router.route(task, context);
        assertEquals("agent-summary", agent.id());

        // 执行
        task = task.start();
        ChatMessage input = ChatMessage.user((String) request.payload().get("text"));
        TaskResult result = agent.execute(input, context);
        task = task.complete();

        // 验证使用了 GPT 模型
        assertTrue(result.isSuccess());
        assertEquals(0, qwenModel.getCallCount()); // 通义模型未被调用
        assertEquals(1, gptModel.getCallCount());   // GPT 被调用

        // 验证输出契约
        TaskResponse response = result.toResponse();
        assertEquals(com.jrl.ai.agent.core.task.contract.ResponseStatus.SUCCESS, response.status());
    }

    @Test
    @DisplayName("完整链路：模型故障转移")
    void testFailoverFlow() {
        // 主模型不可用
        qwenModel.withAvailable(false);

        // 通过 ModelRegistry 查找可用模型
        Optional<Model> primary = modelRegistry.resolve("qwen-max");
        assertTrue(primary.isPresent());
        assertFalse(primary.get().isAvailable());

        // 故障转移到备用模型
        Optional<Model> fallback = modelRegistry.resolve("gpt-4.1");
        assertTrue(fallback.isPresent());
        assertTrue(fallback.get().isAvailable());

        // 使用备用模型创建 Agent 并执行
        MockAgent fallbackAgent = new MockAgent("agent-fallback", "备用助手", fallback.get());
        AgentContext context = AgentContext.builder()
                .sessionId("session-failover")
                .userId("user-bob")
                .build();

        TaskResult result = fallbackAgent.execute(ChatMessage.user("测试故障转移"), context);
        assertTrue(result.isSuccess());
        assertEquals(1, ((MockModel) fallback.get()).getCallCount());
    }

    @Test
    @DisplayName("完整链路：PromptBuilder 构建消息 → Agent 执行")
    void testPromptBuilderFlow() {
        // 用 PromptBuilder 组装消息
        List<ChatMessage> messages = new PromptBuilder()
                .system("你是一个专业的翻译助手，请将内容翻译为{targetLang}")
                .user("Hello World")
                .build();

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role().name().toLowerCase());
        assertEquals("user", messages.get(1).role().name().toLowerCase());

        // 直接用模型调用
        String response = qwenModel.call(messages);
        assertNotNull(response);
        assertEquals(1, qwenModel.getCallCount());
    }

    @Test
    @DisplayName("完整链路：Planner 动态重规划")
    void testReplanFlow() {
        AgentContext context = AgentContext.builder()
                .sessionId("session-replan")
                .userId("user-bob")
                .build();

        Goal goal = new Goal() {
            @Override public String name() { return "complex-task"; }
            @Override public String description() { return "复杂任务"; }
            @Override public boolean isAchieved(Object state) { return false; }
        };

        // 首次规划
        Optional<Plan> plan = planner.plan(goal, context, Map.of());
        assertTrue(plan.isPresent());
        assertEquals(PlanStatus.CREATED, plan.get().status());

        // 模拟执行第一步
        // 检查是否需要重规划
        assertFalse(planner.needsReplan(plan.get(), Map.of("step1", "done")));
    }
}
