package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.mock.MockAgent;
import com.jrl.ai.agent.core.mock.MockModel;
import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.model.ModelConfig;
import com.jrl.ai.agent.core.model.ModelRegistry;
import com.jrl.ai.agent.core.plan.*;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.contract.TokenUsage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多模型协作测试 — 一个任务需要多个大模型参与执行。
 *
 * <p>覆盖三种协作模式：
 * <ul>
 *   <li>流水线模式（Pipeline）— 模型 A → 模型 B → 模型 C 串行处理</li>
 *   <li>并行+汇聚模式（Parallel + Merge）— 多模型同时处理，结果合并</li>
 *   <li>Planner 编排模式 — 由 Planner 动态决定多模型调用顺序</li>
 * </ul>
 */
@DisplayName("多模型协作流程测试")
class MultiModelFlowTest {

    private MockModel translateModel;   // 翻译模型
    private MockModel polishModel;      // 润色模型
    private MockModel qualityModel;     // 质检模型
    private MockModel summaryModel;     // 摘要模型
    private AgentContext context;

    @BeforeEach
    void setUp() {
        translateModel = new MockModel("qwen-translate", "dashscope")
                .withFixedResponse("这是一段翻译后的中文内容。");
        polishModel = new MockModel("qwen-polish", "dashscope")
                .withFixedResponse("经过润色后的优美中文表达。");
        qualityModel = new MockModel("gpt-quality", "openai")
                .withFixedResponse("质检通过，评分：95分。");
        summaryModel = new MockModel("qwen-summary", "dashscope")
                .withFixedResponse("本文核心要点：多模型协作提升任务质量。");

        context = AgentContext.builder()
                .sessionId("session-multi-model")
                .userId("user-alice")
                .build();
    }

    // ========== 模式一：流水线（Pipeline） ==========

    /**
     * 流水线 Agent — 将多个单模型 Agent 串联，前一个的输出作为后一个的输入。
     *
     * <p>适用场景：翻译 → 润色 → 质检，每一步由不同模型负责。
     */
    static class PipelineAgent implements Agent {
        private final String id;
        private final String name;
        private final List<Agent> stages;

        PipelineAgent(String id, String name, List<Agent> stages) {
            this.id = id;
            this.name = name;
            this.stages = List.copyOf(stages);
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }

        @Override
        public TaskResult execute(ChatMessage input, AgentContext ctx) {
            String currentText = input.content();
            long totalDuration = 0;
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            Map<String, Object> allStageResults = new LinkedHashMap<>();

            for (Agent stage : stages) {
                long start = System.currentTimeMillis();
                TaskResult stageResult = stage.execute(ChatMessage.user(currentText), ctx);
                totalDuration += System.currentTimeMillis() - start;

                assertTrue(stageResult.isSuccess(), "阶段 [" + stage.name() + "] 执行失败");

                // 提取文本输出，传递给下一阶段
                currentText = (String) stageResult.result().get("response");
                allStageResults.put(stage.name(), stageResult.result());

                // 累计 Token
                if (stageResult.usage() != null) {
                    totalInputTokens += stageResult.usage().promptTokens();
                    totalOutputTokens += stageResult.usage().completionTokens();
                }
            }

            return TaskResult.success(
                    id, ctx.sessionId(), "text",
                    Map.of("finalResponse", currentText, "stages", allStageResults),
                    TokenUsage.of(totalInputTokens, totalOutputTokens, "pipeline"),
                    totalDuration
            );
        }
    }

    @Test
    @DisplayName("流水线模式：翻译 → 润色 → 质检，三模型串行")
    void testPipelineMode() {
        // 构建流水线：翻译 → 润色 → 质检
        Agent translateAgent = new MockAgent("a-translate", "翻译Agent", translateModel);
        Agent polishAgent = new MockAgent("a-polish", "润色Agent", polishModel);
        Agent qualityAgent = new MockAgent("a-quality", "质检Agent", qualityModel);

        Agent pipeline = new PipelineAgent("pipeline-1", "翻译质检流水线",
                List.of(translateAgent, polishAgent, qualityAgent));

        // 执行
        TaskResult result = pipeline.execute(ChatMessage.user("Hello, this is a test."), context);

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("pipeline-1", result.taskId());

        // 验证三个阶段都执行了
        @SuppressWarnings("unchecked")
        Map<String, Object> stages = (Map<String, Object>) result.result().get("stages");
        assertEquals(3, stages.size());
        assertTrue(stages.containsKey("翻译Agent"));
        assertTrue(stages.containsKey("润色Agent"));
        assertTrue(stages.containsKey("质检Agent"));

        // 验证每个模型都被调用了一次
        assertEquals(1, translateModel.getCallCount());
        assertEquals(1, polishModel.getCallCount());
        assertEquals(1, qualityModel.getCallCount());

        // 验证 Token 累计（每个模型 10 input + response.length() output）
        assertEquals(30, result.usage().promptTokens());
    }

    // ========== 模式二：并行 + 汇聚（Parallel + Merge） ==========

    /**
     * 并行 Agent — 多个模型同时处理同一输入，汇聚所有结果。
     *
     * <p>适用场景：多模型投票、多视角分析、结果对比。
     */
    static class ParallelAgent implements Agent {
        private final String id;
        private final String name;
        private final List<Agent> workers;

        ParallelAgent(String id, String name, List<Agent> workers) {
            this.id = id;
            this.name = name;
            this.workers = List.copyOf(workers);
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }

        @Override
        public TaskResult execute(ChatMessage input, AgentContext ctx) {
            // 并行提交所有 worker
            ExecutorService executor = Executors.newFixedThreadPool(workers.size());
            List<Future<TaskResult>> futures = workers.stream()
                    .map(w -> executor.submit(() -> w.execute(input, ctx)))
                    .toList();

            // 收集结果
            Map<String, Object> mergedResults = new LinkedHashMap<>();
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            long maxDuration = 0;

            for (int i = 0; i < workers.size(); i++) {
                try {
                    TaskResult r = futures.get(i).get(30, TimeUnit.SECONDS);
                    assertTrue(r.isSuccess());
                    mergedResults.put(workers.get(i).name(), r.result());
                    if (r.usage() != null) {
                        totalInputTokens += r.usage().promptTokens();
                        totalOutputTokens += r.usage().completionTokens();
                    }
                    maxDuration = Math.max(maxDuration, r.durationMs());
                } catch (Exception e) {
                    throw new RuntimeException("并行执行失败: " + workers.get(i).name(), e);
                }
            }
            executor.shutdown();

            return TaskResult.success(
                    id, ctx.sessionId(), "merged",
                    Map.of("responses", mergedResults, "workerCount", workers.size()),
                    TokenUsage.of(totalInputTokens, totalOutputTokens, "parallel"),
                    maxDuration
            );
        }
    }

    @Test
    @DisplayName("并行模式：三模型同时处理，结果汇聚")
    void testParallelMode() {
        Agent translateAgent = new MockAgent("a-translate", "翻译Agent", translateModel);
        Agent summaryAgent = new MockAgent("a-summary", "摘要Agent", summaryModel);
        Agent qualityAgent = new MockAgent("a-quality", "质检Agent", qualityModel);

        Agent parallel = new ParallelAgent("parallel-1", "并行分析",
                List.of(translateAgent, summaryAgent, qualityAgent));

        // 执行
        TaskResult result = parallel.execute(ChatMessage.user("一段需要多角度分析的文本"), context);

        // 验证
        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> responses = (Map<String, Object>) result.result().get("responses");
        assertEquals(3, responses.size());
        assertEquals(3, result.result().get("workerCount"));

        // 每个模型各调用一次
        assertEquals(1, translateModel.getCallCount());
        assertEquals(1, summaryModel.getCallCount());
        assertEquals(1, qualityModel.getCallCount());
    }

    // ========== 模式三：Planner 编排 ==========

    /**
     * Planner 驱动的编排器 — 根据计划动态调度多个模型。
     *
     * <p>Planner 生成包含模型分配的执行计划，编排器按计划逐步调用对应模型。
     */
    @Test
    @DisplayName("Planner 编排模式：动态分配模型执行多步任务")
    void testPlannerOrchestration() {
        // 注册所有可用模型
        Map<String, Model> modelStore = new HashMap<>();
        modelStore.put(translateModel.modelId(), translateModel);
        modelStore.put(polishModel.modelId(), polishModel);
        modelStore.put(qualityModel.modelId(), qualityModel);
        modelStore.put(summaryModel.modelId(), summaryModel);

        ModelRegistry registry = new ModelRegistry() {
            private Model defaultModel;
            @Override public void register(Model m) { modelStore.put(m.modelId(), m); if (defaultModel == null) defaultModel = m; }
            @Override public Optional<Model> resolve(String ref) {
                String id = ref.contains(":") ? ref.split(":")[1] : ref;
                return Optional.ofNullable(modelStore.get(id));
            }
            @Override public Optional<Model> defaultModel() { return Optional.ofNullable(defaultModel); }
            @Override public Collection<Model> all() { return List.copyOf(modelStore.values()); }
        };
        modelStore.values().forEach(registry::register);

        // 定义目标
        Goal goal = new Goal() {
            @Override public String name() { return "multi-model-process"; }
            @Override public String description() { return "多模型协作处理文本"; }
            @Override public boolean isAchieved(Object state) { return false; }
        };

        // Planner 生成计划：每步指定使用哪个模型
        Planner planner = new Planner() {
            @Override
            public Optional<Plan> plan(Goal g, AgentContext ctx, Object state) {
                List<PlanStep> steps = List.of(
                        PlanStep.pending("翻译", 0, "原文存在", "翻译完成"),
                        PlanStep.pending("润色", 1, "翻译完成", "润色完成"),
                        PlanStep.pending("质检", 2, "润色完成", "质检完成"),
                        PlanStep.pending("摘要", 3, "质检完成", "摘要完成")
                );
                // 为每步绑定模型 ID（通过 description 约定）
                return Optional.of(Plan.create(g, steps));
            }
            @Override
            public boolean needsReplan(Plan currentPlan, Object state) { return false; }
        };

        // 步骤 → 模型映射（实际场景中由 Planner 或 LLM 决定）
        Map<String, String> stepModelMapping = Map.of(
                "翻译", "qwen-translate",
                "润色", "qwen-polish",
                "质检", "gpt-quality",
                "摘要", "qwen-summary"
        );

        // 编排器：按计划逐步执行
        Optional<Plan> planOpt = planner.plan(goal, context, Map.of());
        assertTrue(planOpt.isPresent());

        Plan plan = planOpt.get();
        assertEquals(4, plan.steps().size());
        String currentText = "Hello, this is a test document.";
        Map<String, Object> executionLog = new LinkedHashMap<>();

        for (PlanStep step : plan.steps()) {
            String modelId = stepModelMapping.get(step.description());
            assertNotNull(modelId, "步骤 [" + step.description() + "] 未分配模型");

            Model model = registry.resolve(modelId).orElseThrow();
            assertTrue(model.isAvailable());

            // 用对应模型执行
            List<ChatMessage> messages = List.of(
                    ChatMessage.system("执行步骤: " + step.description()),
                    ChatMessage.user(currentText)
            );
            currentText = model.call(messages);
            executionLog.put(step.description(), Map.of(
                    "model", modelId,
                    "output", currentText
            ));
        }

        // 验证：4个步骤都执行了，各模型各调用一次
        assertEquals(4, executionLog.size());
        assertEquals(1, translateModel.getCallCount());
        assertEquals(1, polishModel.getCallCount());
        assertEquals(1, qualityModel.getCallCount());
        assertEquals(1, summaryModel.getCallCount());
    }

    // ========== 模式四：动态重规划 + 模型切换 ==========

    @Test
    @DisplayName("重规划模式：执行中模型不可用，动态切换")
    void testReplanWithModelSwitch() {
        // 主翻译模型
        MockModel primaryTranslate = new MockModel("primary-translate", "dashscope")
                .withFixedResponse("主模型翻译结果");
        // 备用翻译模型
        MockModel fallbackTranslate = new MockModel("fallback-translate", "openai")
                .withFixedResponse("备用模型翻译结果");

        Map<String, Model> modelStore = new HashMap<>();
        modelStore.put(primaryTranslate.modelId(), primaryTranslate);
        modelStore.put(fallbackTranslate.modelId(), fallbackTranslate);

        ModelRegistry registry = new ModelRegistry() {
            private Model defaultModel;
            @Override public void register(Model m) { modelStore.put(m.modelId(), m); if (defaultModel == null) defaultModel = m; }
            @Override public Optional<Model> resolve(String ref) {
                String id = ref.contains(":") ? ref.split(":")[1] : ref;
                return Optional.ofNullable(modelStore.get(id));
            }
            @Override public Optional<Model> defaultModel() { return Optional.ofNullable(defaultModel); }
            @Override public Collection<Model> all() { return List.copyOf(modelStore.values()); }
        };
        modelStore.values().forEach(registry::register);

        // 第一步：主模型可用，正常执行
        Model primary = registry.resolve("primary-translate").orElseThrow();
        assertTrue(primary.isAvailable());
        String result1 = primary.call(List.of(ChatMessage.user("test")));
        assertEquals("主模型翻译结果", result1);

        // 第二步：主模型突然不可用
        primaryTranslate.withAvailable(false);
        assertFalse(primary.isAvailable());

        // 第三步：重规划 — 切换到备用模型
        Model fallback = registry.resolve("fallback-translate").orElseThrow();
        assertTrue(fallback.isAvailable());
        String result2 = fallback.call(List.of(ChatMessage.user("test")));
        assertEquals("备用模型翻译结果", result2);

        // 验证
        assertEquals(1, primaryTranslate.getCallCount());
        assertEquals(1, fallbackTranslate.getCallCount());
    }

    // ========== 模式五：Pipeline + Parallel 混合 ==========

    @Test
    @DisplayName("混合模式：先并行分析，再串行质检")
    void testMixedPipelineAndParallel() {
        // 阶段1：并行 — 翻译 + 摘要同时进行
        Agent translateAgent = new MockAgent("a-translate", "翻译Agent", translateModel);
        Agent summaryAgent = new MockAgent("a-summary", "摘要Agent", summaryModel);
        Agent parallelStage = new ParallelAgent("parallel", "并行分析",
                List.of(translateAgent, summaryAgent));

        // 阶段2：串行 — 质检（基于并行结果）
        Agent qualityAgent = new MockAgent("a-quality", "质检Agent", qualityModel);

        // 手动编排：先并行，再串行
        TaskResult parallelResult = parallelStage.execute(
                ChatMessage.user("需要并行处理的文本"), context);
        assertTrue(parallelResult.isSuccess());

        // 将并行结果合并为质检输入
        String mergedInput = parallelResult.result().toString();
        TaskResult qualityResult = qualityAgent.execute(
                ChatMessage.user(mergedInput), context);
        assertTrue(qualityResult.isSuccess());

        // 验证所有模型都被调用
        assertEquals(1, translateModel.getCallCount());
        assertEquals(1, summaryModel.getCallCount());
        assertEquals(1, qualityModel.getCallCount());
    }
}
