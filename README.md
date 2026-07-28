# JAgentFramework

[![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.7-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![AgentScope](https://img.shields.io/badge/AgentScope-2.0.0-blue)](https://github.com/agentscope-io/agentscope-java)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**JAgentFramework** 是一个面向 Java 生态的通用 AI Agent 编排框架。它在底层 Agent 执行引擎（如 AgentScope）之上提供统一的抽象层，赋予开发者 **多模型路由、Skill 动态评分、拦截器链、任务编排、Spring Boot 自动装配** 等企业级能力。

> 一句话概括：**不是又一个 Agent 实现，而是管理 Agent 的框架。**

---

## 特性一览

| 能力 | 说明 |
|------|------|
| **多模型协作** | `Router` + `ModelRegistry` 支持 `provider:model` 格式动态路由，Pipeline / Parallel 编排多模型协作流程 |
| **统一拦截器链** | `AgentInterceptor` / `SkillInterceptor` / `MemoryInterceptor` 三层 AOP 切面，支持 before / after / around / onError |
| **Skill 动态评分** | 按 Agent 维度配置 Skill 优先级权重，结合运行时成功率自动评分排序，选择最优 Skill |
| **GOAP 规划** | 借鉴 Embabel 的 Goal-Plan-Step 模型，支持动态规划与重评估 |
| **反馈调节** | `FeedbackHandler` 机制支持 Prompt 反馈和 Skill 反馈两种调节模式 |
| **提示词管理** | `PromptTemplate` + `PromptRegistry` 支持模板化提示词注册与渲染 |
| **Spring Boot 集成** | `@Import(JAgentAutoConfiguration.class)` 一键启用，YAML 配置驱动 |
| **Micrometer 监控** | 自动采集 Agent 执行耗时、Skill 调用次数、Token 消耗等指标 |
| **可观测性** | `ExecutionTrace` 全链路追踪，记录每步执行详情 |
| **五维评测系统** | 智能/性能/可靠性/安全/体验五维评估，支持规则评测 + LLM 评测 + 自定义 Agent 评测 |
| **置信度阈值与自动优化** | 可配置置信度阈值，低于阈值时自动触发 LLM 优化分析，生成 Prompt / Skill / 模型 / 编排四维优化建议 |
| **评测步骤自动追踪** | 评测步骤自动合并到 Agent 主链路 trace，含评测模型信息，全程可观测 |

---

## 架构设计

```
┌──────────────────────────────────────────────────────────┐
│                    jagent-demo                           │
│              （业务示例：智能打标、翻译、摘要）              │
├──────────────────────────────────────────────────────────┤
│                 jagent-agentscope                        │
│  ┌─────────────┬──────────────┬───────────────────────┐  │
│  │ Agent 适配   │ Skill 桥接    │ Spring Boot 自动装配  │  │
│  │ AgentScope   │ SkillTool    │ JAgentAutoConfig     │  │
│  │ AgentAdapter │ Adapter      │ AgentFactory         │  │
│  ├─────────────┼──────────────┤ SkillScoring          │  │
│  │ Model 桥接   │ 拦截器实现    │ Interceptor          │  │
│  │ AgentScope   │ Metrics      │ Evaluation           │  │
│  │ ModelRegistry│ Interceptor  │ Interceptor          │  │
│  └─────────────┴──────────────┴───────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│                    jagent-core                           │
│  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────────┐  │
│  │agent │skill │model │prompt│plan  │router│feedback  │  │
│  │Agent │Skill │Model │Prompt│Goal  │Router│Feedback  │  │
│  │Life- │Reg-  │Reg-  │Temp- │Plan- │      │Handler   │  │
│  │cycle │istry │istry │late  │ner   │      │          │  │
│  └──────┴──────┴──────┴──────┴──────┴──────┴──────────┘  │
│  ┌──────┬──────┬──────┬──────┬──────┬──────────────────┐  │
│  │context│ task │  io  │retr- │stor- │    monitor      │  │
│  │Agent │Task  │Chat  │rieve │KV    │  Metrics         │  │
│  │Ctx   │Result│Msg   │r     │Store │  Interceptor     │  │
│  └──────┴──────┴──────┴──────┴──────┴──────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   evaluation                         │  │
│  │  Evaluator  EvaluationStore  CompositeScorer         │  │
│  │  五维评估模型    分层评测      评测持久化              │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

**分层原则：**
- **jagent-core** — 纯抽象层，仅依赖 JDK + SLF4J，零框架依赖
- **jagent-agentscope** — 实现层，桥接 AgentScope 2.0 + Spring Boot 自动装配
- **jagent-demo** — 业务示例，展示框架实际使用方式

---

## 快速开始

### 环境要求

- Java 21+（推荐 Java 25）
- Gradle 9.x
- AgentScope 2.0.0

### 1. 配置仓库

GitHub Packages 需要配置仓库地址和认证：

**Gradle（settings.gradle）：**
```groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/long172066912/JAgentFramework")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

**Maven（settings.xml）：**
```xml
<settings>
  <profiles>
    <profile>
      <id>github</id>
      <repositories>
        <repository>
          <id>github</id>
          <url>https://maven.pkg.github.com/long172066912/JAgentFramework</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>github</activeProfile>
  </activeProfiles>
  <servers>
    <server>
      <id>github</id>
      <username>long172066912</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

### 2. 引入依赖

```groovy
// Gradle
dependencies {
    implementation 'com.jrl.ai:jagent-agentscope:0.3.0'
    // 如果使用 DashScope 模型
    implementation 'io.agentscope:agentscope-extensions-model-dashscope:2.0.0'
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>com.jrl.ai</groupId>
    <artifactId>jagent-agentscope</artifactId>
    <version>0.3.0</version>
</dependency>
```

### 3. 配置 application.yml

```yaml
jagent:
  workspace: "./workspace"
  model:
    api-keys:
      dashscope: ${DASHSCOPE_API_KEY}
  agents:
    translator:
      name: "翻译助手"
      sys-prompt: "你是一个中英互译助手"
      model: "dashscope:qwen3.7-flash"
      max-iters: 10
      skill-priorities:
        vector_search: 0.9
        vector_get: 0.5
  # 评测系统配置（v0.2.0 新增）
  evaluation:
    enabled: true                        # 启用评测
    llm-judge-enabled: false             # 启用 LLM 评测（按需）
    llm-judge-model: "dashscope:qwen-plus"
    llm-judge-prompt: ""                 # 自定义 LLM 评测提示词（可选，%s 占位符用于输入/输出）
    latency-threshold-ms: 10000          # 性能阈值
    weights:                             # 五维权重
      intelligence: 0.3
      performance: 0.15
      reliability: 0.2
      safety: 0.2
      experience: 0.15
    optimization:                        # 优化建议配置（v0.3.0 新增）
      enabled: true                      # 启用自动优化建议
      llm-enabled: true                  # 启用 LLM 优化分析
      llm-model: "dashscope:qwen-plus"   # 优化分析模型
      confidence-threshold: 0.9          # 置信度阈值，低于此值触发优化建议
```

### 4. 启动应用

```java
@SpringBootApplication
@EnableConfigurationProperties(JAgentProperties.class)
@Import(JAgentAutoConfiguration.class)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 5. 调用 Agent

```java
@RestController
public class ChatController {

    private final AgentFactory agentFactory;

    public ChatController(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String text) {
        Agent agent = agentFactory.getAgent("translator");
        TaskResult result = agent.execute(ChatMessage.user(text), AgentContext.builder().build());
        return (String) result.result().get("response");
    }
}
```

---

## 核心概念

### Agent 与拦截器链

```
用户输入 → AgentInterceptor.beforeExecute()
                ↓
         AgentInterceptor.aroundExecute()
                ↓
         HarnessAgent.call()  ← AgentScope 执行推理
                ↓
         AgentInterceptor.afterExecute()
                ↓
           返回结果
```

### Skill 评分机制

每个 Agent 可独立配置 Skill 优先级权重，运行时结合历史成功率动态评分：

```
最终评分 = 配置优先级 × 0.4 + 动态成功率 × 0.6
```

```yaml
jagent:
  agents:
    tagger:
      skill-priorities:
        vector_search: 0.9   # 打标助手偏好搜索
        vector_upsert: 0.7
```

### 自定义 Skill

```java
public class MySkill implements Skill {
    @Override public String name() { return "my_tool"; }
    @Override public String description() { return "我的自定义工具"; }
    @Override public SkillResult execute(SkillContext context) {
        // 业务逻辑
        return SkillResult.success("my_tool", "执行结果", 10);
    }
}

// 注册
@Bean
public SkillRegistry mySkillRegistry() {
    SkillRegistry registry = new DefaultSkillRegistry();
    registry.register(new MySkill());
    return registry;
}
```

注册后 Skill 会自动通过 `SkillToolAdapter` 桥接为 AgentScope 的 `AgentTool`，挂载到 HarnessAgent 的 Toolkit 中。

### 五维评测系统

Agent 输出质量从五个维度综合评测：

| 维度 | 说明 | 评测层级 |
|------|------|------|
| **Intelligence** | 智能：输出质量、相关性、完整性 | Tier2 LLM 评测 |
| **Performance** | 性能：延迟、吞吐量、Token 消耗 | Tier1 规则评测 |
| **Reliability** | 可靠性：成功率、一致性 | Tier1 规则评测 |
| **Safety** | 安全：内容安全、Prompt 泄露 | Tier1 + Tier2 |
| **Experience** | 体验：用户满意度 | Tier3 人工反馈 |

**配置即启用：**
```yaml
jagent:
  evaluation:
    enabled: true              # 启用评测
    llm-judge-enabled: true    # 启用 LLM 评测
    llm-judge-prompt: ""       # 自定义 LLM 评测提示词（可选）
```

**自定义评测器（用自己的 Agent 做评测）：**
```java
@Bean
public Evaluator myAgentEvaluator(Agent myJudgeAgent) {
    return new AgentEvaluator(myJudgeAgent, EvaluationDimension.INTELLIGENCE);
}
```

### 置信度阈值与自动优化建议（v0.3.0 新增）

当评测综合分低于配置的置信度阈值时，系统自动触发 LLM 优化分析，从四个维度生成改进建议：

| 优化维度 | 说明 |
|----------|------|
| **PROMPT** | 提示词优化建议 |
| **SKILL** | Skill 工具增强建议 |
| **MODEL** | 模型选择与替换建议 |
| **AGENT_STEP** | Agent 编排流程优化建议 |

```yaml
jagent:
  evaluation:
    optimization:
      enabled: true                     # 启用自动优化建议
      llm-enabled: true                 # 使用 LLM 进行优化分析
      llm-model: "dashscope:qwen-plus"  # 优化分析所用模型
      confidence-threshold: 0.9         # 低于此分数自动触发优化
```

评测步骤（`EVAL_RULEBASEDEVALUATOR`、`EVAL_LLMJUDGEEVALUATOR`、`COMPOSITE_SCORE`）会自动合并到 Agent 主链路 trace 中，评测步骤详情包含评测模型信息，全程可观测。

---

## 模块详解

### jagent-core — 纯抽象层

零框架依赖（仅 JDK + SLF4J），定义 AI Agent 的全部接口契约与扩展点。

#### agent 包 — Agent 核心抽象

| 接口 | 职责 |
|------|------|
| `Agent` | 智能体基本契约：`id()` / `name()` / `execute()` / `supportsStreaming()` |
| `AgentInterceptor` | 四段拦截：`beforeExecute` / `afterExecute` / `onError` / `aroundExecute`（含 `ExecutionChain`） |
| `AgentLifecycle` | 生命周期管理：创建、启动、停止、销毁 |
| `AgentRegistry` | Agent 注册表：`register` / `get` / `unregister` / `all` |

```java
// 自定义拦截器示例
public class LoggingInterceptor implements AgentInterceptor {
    @Override
    public void beforeExecute(Agent agent, ChatMessage input, AgentContext ctx) {
        log.info("Agent [{}] 开始执行, sessionId={}", agent.name(), ctx.sessionId());
    }
    @Override
    public void afterExecute(Agent agent, ChatMessage input, AgentContext ctx, TaskResult result) {
        log.info("Agent [{}] 执行完成, 耗时={}ms", agent.name(), result.durationMs());
    }
}
```

#### skill 包 — Skill 能力体系

| 接口/类 | 职责 |
|------|------|
| `Skill` | 技能抽象：`name()` / `description()` / `execute()` / `isAvailable()` |
| `SkillContext` | 技能执行上下文：skillName + input + AgentContext + 原始参数 |
| `SkillResult` | 执行结果：success/fail + output + 耗时 |
| `SkillRegistry` | 注册表：`register` / `get` / `unregister` / `all` |
| `DefaultSkillRegistry` | 默认实现（线程安全 ConcurrentHashMap） |
| `ScoringSkillRegistry` | 评分感知注册表：`getBest(agentId)` / `rank(agentId)` |
| `SkillInterceptor` | Skill 层拦截：before / after / onError / around |
| `SkillScorer` | 评分器接口：`score(agentId, skill)` / `rank()` / `recordExecution()` |
| `Tool` | 工具注解标记（供反射发现） |

```java
// 评分排序示例
ScoringSkillRegistry registry = new ScoringSkillRegistry(delegate, scorer);
Skill best = registry.getBest("tagger");       // 获取评分最高的 Skill
List<Skill> ranked = registry.rank("tagger");   // 按评分降序排列所有 Skill
```

#### model 包 — 多模型抽象

| 接口 | 职责 |
|------|------|
| `Model` | 模型抽象：`modelName()` / `provider()` / `generate()` / `supportsStreaming()` |
| `ModelConfig` | 模型配置：provider + modelName + 参数 |
| `ModelRegistry` | 注册表：`register` / `resolve(ref)` / `canResolve(ref)` / `all` |

模型引用格式为 `provider:modelName`（如 `dashscope:qwen-plus`），Registry 自动解析。

#### plan 包 — GOAP 规划

| 接口/类 | 职责 |
|------|------|
| `Planner` | 规划器：`plan(goal, ctx, state)` / `needsReplan(currentPlan, state)` |
| `Goal` | 目标定义：名称 + 描述 + 前置/后置条件 |
| `Plan` | 执行计划：目标 + 步骤列表 + 状态 |
| `PlanStep` | 计划步骤：动作名称 + 前置条件 + 后置效果 + 代价 |
| `PlanStatus` | 计划状态枚举：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED |

#### prompt 包 — 提示词管理

| 接口 | 职责 |
|------|------|
| `PromptTemplate` | 模板抽象：`name()` / `render(variables)` |
| `PromptBuilder` | 流式构建器：链式添加变量和段落 |
| `PromptRegistry` | 模板注册表：`register` / `get` / `all` |

#### 其他包

| 包 | 核心类 | 职责 |
|------|------|------|
| `context` | `AgentContext` | 运行时上下文：sessionId / userId / 扩展属性 Map |
| `io` | `ChatMessage` / `MessageRole` | 消息抽象：user / assistant / system 角色 |
| `router` | `Router` | 任务路由：根据 Task + Context 选择目标 Agent |
| `feedback` | `FeedbackHandler` / `Feedback` | 反馈调节：Prompt 反馈 + Skill 反馈 |
| `retrieval` | `Retriever` / `RetrievalResult` | 知识检索抽象 |
| `storage` | `KVStore` | KV 存储抽象 |
| `memory` | `MemoryStore` / `MemoryInterceptor` | 记忆存储 + 拦截 |
| `monitor` | `MetricsInterceptor` | Micrometer 指标采集（同时适配 Agent/Skill/Memory 三层） |
| `task` | `Task` / `TaskResult` / `ExecutionTrace` | 任务契约 + 执行追踪 |
| `task/contract` | `TaskRequest` / `TaskResponse` / `TokenUsage` | 传输无关的标准化协议 |
| `evaluation` | `Evaluator` / `EvaluationStore` / `CompositeScorer` | 五维评测：接口 + 数据模型 |

---

### jagent-agentscope — 适配层 + Spring Boot 集成

桥接 AgentScope 2.0 框架，同时完成 Spring Boot 自动装配。

#### adapter 包 — 适配器

| 类 | 职责 |
|------|------|
| `AgentScopeAgentAdapter` | 将 AgentScope `HarnessAgent` 包装为 jagent `Agent` 接口，驱动拦截器链 |
| `AgentScopeModelAdapter` | 将 AgentScope `ChatModel` 包装为 jagent `Model` 接口 |
| `MessageConverter` | jagent `ChatMessage` ↔ AgentScope `Msg` 双向转换 |
| `ContextConverter` | jagent `AgentContext` ↔ AgentScope `RuntimeContext` 双向转换 |

#### config 包 — 自动装配

| 类 | 职责 |
|------|------|
| `JAgentAutoConfiguration` | Spring Boot 自动装配入口，注册所有 Bean |
| `JAgentProperties` | YAML 配置绑定（`jagent.*` 前缀） |
| `AgentFactory` | Agent 工厂：懒创建 + 缓存，自动挂载 Skill 到 Toolkit |

**自动装配的 Bean 清单：**

| Bean | 实现类 | 条件 |
|------|------|------|
| `AgentFactory` (→ `AgentRegistry`) | `AgentFactory` | 始终 |
| `ModelRegistry` | `AgentScopeModelRegistry` | 始终 |
| `PromptRegistry` | `InMemoryPromptRegistry` | 始终 |
| `KVStore` | `JsonFileKVStore` | 始终 |
| `Router` | `DefaultRouter` | 始终 |
| `AgentLifecycle` | `AgentLifecycleManager` | 始终 |
| `Planner` | `AgentScopePlanner` | 始终 |
| `Retriever` | `AgentScopeRetriever` | classpath 存在 `Knowledge` |
| `MetricsInterceptor` | `MetricsInterceptor` | classpath 存在 `MeterRegistry` |
| `SkillScoringInterceptor` | `SkillScoringInterceptor` | 始终 |
| `SkillScorer` | → `SkillScoringInterceptor` | `@ConditionalOnMissingBean` |
| `RuleBasedEvaluator` | `RuleBasedEvaluator` | `jagent.evaluation.enabled=true` |
| `LLMJudgeEvaluator` | `LLMJudgeEvaluator` | `jagent.evaluation.llm-judge-enabled=true` |
| `CompositeScorer` | `DefaultCompositeScorer` | `jagent.evaluation.enabled=true` |
| `EvaluationStore` | `JsonFileEvaluationStore` | `jagent.evaluation.enabled=true` |
| `EvaluationInterceptor` | `EvaluationInterceptor` | `jagent.evaluation.enabled=true` |
| `OptimizationReportStore` | `JsonFileOptimizationReportStore` | `jagent.evaluation.enabled=true` |
| `OptimizationAnalyzer` | `RuleBasedOptimizationAnalyzer` | `jagent.evaluation.optimization.enabled=true` |
| `OptimizationAnalyzer` | `LLMBasedOptimizationAnalyzer` | `jagent.evaluation.optimization.llm-enabled=true` |

#### skill 包 — Skill 桥接

| 类 | 职责 |
|------|------|
| `SkillToolAdapter` | 将 jagent `Skill` 包装为 AgentScope `AgentTool`，自动挂载到 HarnessAgent 的 Toolkit |
| `SkillScoringInterceptor` | 同时实现 `SkillInterceptor` + `SkillScorer`，在 before/after 中记录统计并评分 |

**Skill 自动挂载流程：**
```
SkillRegistry Bean 存在
    ↓
AgentFactory 构建 HarnessAgent 时
    ↓
遍历 SkillRegistry.all() → 每个 Skill 包装为 SkillToolAdapter
    ↓
注册到 Toolkit → HarnessAgent.toolkit(toolkit)
    ↓
Agent 推理时 LLM 可发现并调用这些 Skill
```

#### 其他适配类

| 类 | 适配方向 |
|------|------|
| `AgentScopeModelRegistry` | 桥接 AgentScope `ModelRegistry` → jagent `ModelRegistry` |
| `AgentScopePlanner` | 对接 AgentScope Plan Mode → jagent `Planner` |
| `AgentScopeRetriever` | 对接 AgentScope `Knowledge.retrieve()` → jagent `Retriever` |
| `InMemoryPromptRegistry` | 内存级 PromptTemplate 注册表 |
| `SimplePromptTemplate` | 基于 `{variable}` 占位符的简单模板引擎 |
| `DefaultRouter` | 基于任务类型的简单路由 |
| `JsonFileKVStore` | 基于 JSON 文件的 KV 持久化存储 |
| `AgentLifecycleManager` | Agent 创建/销毁生命周期管理 |

#### evaluation 包 — 评测系统实现

| 类 | 职责 |
|------|------|
| `RuleBasedEvaluator` | Tier1 零成本规则评测：性能(延迟阈值)、可靠性(成功率)、安全(敏感词/Prompt泄露) |
| `LLMJudgeEvaluator` | Tier2 LLM 语义评测：调用 ChatModel 对输出打分（智能+安全） |
| `AgentEvaluator` | 将任意 Agent 包装为 Evaluator，用户可用自己的 Agent 做评测 |
| `EvaluationInterceptor` | AgentInterceptor 实现，afterExecute 自动触发评测链 |
| `JsonFileEvaluationStore` | JSON 文件持久化，复用 workspace 目录 |
| `JsonFileOptimizationReportStore` | 优化报告 JSON 文件持久化存储 |
| `RuleBasedOptimizationAnalyzer` | 基于规则的优化分析器，根据评测结果生成规则化建议 |
| `LLMBasedOptimizationAnalyzer` | 基于 LLM 的优化分析器，调用大模型生成 PROMPT/SKILL/MODEL/AGENT_STEP 四维优化建议 |
| `DefaultOutputFeedbackHandler` | 人工反馈处理，关联到评测结果 |

---

### jagent-demo — 业务示例

| 包 | 说明 |
|------|------|
| `controller/AgentController` | REST 端点：同步对话、SSE 流式、Agent 列表 |
| `controller/EvaluationController` | 评测 API：查看评分、历史、聚合指标、人工反馈 |
| `controller/EvaluationDemoController` | 评测演示：执行对话+自动评测、查看统计 |
| `controller/SkillScoringController` | Skill 评分演示：模拟执行、查看评分、批量模拟 |
| `service/AgentService` | Agent 调用封装：同步 + 流式两种模式 |
| `tagging/` | 完整智能打标业务：Agent 调用 + Skill 挂载 + 标签解析 + 向量存储 + 回执机制 |
| `tagging/skill/` | 向量存储 Skill：`VectorSearchSkill` / `VectorUpsertSkill` / `VectorGetSkill` |
| `tagging/service/TaggingService` | 打标核心流程：LLM 调用 + 标签数量校验重试 + 向量生成 + Milvus 写入 |
| `tagging/mq/` | 任务消费 + 回执分发：`TaskConsumer` / `CallbackDispatcher`（MQ/HTTP/不回执） |

---

## 完整配置参考

```yaml
jagent:
  # 工作空间路径（Agent 会话持久化、文件操作根目录）
  workspace: "./workspace"

  # 模型 API Key 配置
  model:
    api-keys:
      dashscope: ${DASHSCOPE_API_KEY}

  # Agent 声明列表
  agents:
    translator:
      name: "翻译助手"                    # 显示名称
      sys-prompt: "你是中英互译助手"          # 系统提示词
      model: "dashscope:qwen3.7-flash"     # 模型引用（provider:model）
      max-iters: 10                        # 最大推理迭代
      max-retries: 3                       # 最大重试次数
      skill-priorities:                    # Skill 优先级（Agent 粒度）
        vector_search: 0.3
        vector_get: 0.8

    tagger:
      name: "智能打标助手"
      sys-prompt: "你是一个专业的内容标签抽取引擎..."
      model: "dashscope:qwen3.7-flash"
      max-iters: 10
      max-retries: 3
      skill-priorities:
        vector_search: 0.9
        vector_upsert: 0.7
        vector_get: 0.5

  # 评测系统配置（v0.2.0 新增）
  evaluation:
    enabled: true                        # 启用评测
    llm-judge-enabled: false             # 启用 LLM 评测（按需）
    llm-judge-model: "dashscope:qwen-plus"  # LLM 评测模型
    llm-judge-prompt: ""                 # 自定义 LLM 评测提示词（可选）
    latency-threshold-ms: 10000          # 性能阈值
    weights:                             # 五维权重
      intelligence: 0.3
      performance: 0.15
      reliability: 0.2
      safety: 0.2
      experience: 0.15
    optimization:                        # 优化建议配置（v0.3.0 新增）
      enabled: true                      # 启用自动优化建议
      llm-enabled: true                  # 启用 LLM 优化分析
      llm-model: "dashscope:qwen-plus"   # 优化分析模型
      confidence-threshold: 0.9          # 置信度阈值
```

---

## Demo 项目

`jagent-demo` 包含完整的智能打标示例，展示了：

- Agent 配置与调用
- Skill 注册与自动挂载
- Skill 评分与动态选择
- 标签数量校验与重试
- 任务回执（MQ / HTTP / 不回执）

```bash
# 启动 demo
export DASHSCOPE_API_KEY=sk-xxx
./gradlew :jagent-demo:bootRun

# 同步打标
curl -X POST http://localhost:8080/api/tagging/tag \
  -H "Content-Type: application/json" \
  -d '{
    "contentId": "001",
    "contentType": "product",
    "contentText": "复古胶片风相机，温暖治愈的色调",
    "requiredTagCount": 5
  }'

# 查看 Skill 评分
curl http://localhost:8080/api/skill-scoring/scores/tagger

# 评测演示：执行对话 + 自动评测
curl -X POST "http://localhost:8080/api/demo/evaluation/run?agentKey=translator&input=Hello"

# 查看评测统计
curl http://localhost:8080/api/demo/evaluation/stats/translator
```

---

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 25（最低 21） |
| Spring Boot | 3.4.7 |
| AgentScope | 2.0.0 |
| Micrometer | 1.14.4 |
| Gradle | 9.2.0 |
| SLF4J | 2.0.13 |

---

## 构建

```bash
# 编译
./gradlew compileJava

# 运行测试
./gradlew test

# 构建所有模块
./gradlew build

# 发布到本地 Maven 仓库（调试用）
./gradlew publishToMavenLocal

# 发布到 GitHub Packages（需配置环境变量）
export GITHUB_ACTOR=long172066912
export GITHUB_TOKEN=ghp_xxxxxxxxxxxx
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

### 发布流程

1. **创建 GitHub Release**：在 GitHub 仓库创建 Release 标签（如 `v0.3.0`），GitHub Actions 会自动发布到 GitHub Packages
2. **手动发布**：
   ```bash
   # 修改版本号
   # build.gradle: version = '0.3.0'
   
   # 发布
   ./gradlew publishAllPublicationsToGitHubPackagesRepository
   ```

---

## License

Apache License 2.0
