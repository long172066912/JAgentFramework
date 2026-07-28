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
│  │ AgentScope   │ Metrics      │                      │  │
│  │ ModelRegistry│ Interceptor  │                      │  │
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

### 1. 引入依赖

```groovy
dependencies {
    implementation 'com.jrl.ai:jagent-agentscope:0.1.0-SNAPSHOT'
    // 如果使用 DashScope 模型
    implementation 'io.agentscope:agentscope-extensions-model-dashscope:2.0.0'
}
```

### 2. 配置 application.yml

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
```

### 3. 启动应用

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

### 4. 调用 Agent

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

---

## 模块说明

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **jagent-core** | 纯抽象层，定义接口与实体 | `Agent`, `Skill`, `ModelRegistry`, `Router`, `Planner`, `FeedbackHandler` |
| **jagent-agentscope** | AgentScope 适配 + Spring Boot 集成 | `AgentScopeAgentAdapter`, `SkillToolAdapter`, `JAgentAutoConfiguration`, `AgentFactory` |
| **jagent-demo** | 业务示例 | 智能打标（含 Skill 评分演示）、翻译、摘要 |

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
```

---

## License

Apache License 2.0
