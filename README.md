# JAgentFramework

[![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.7-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![AgentScope](https://img.shields.io/badge/AgentScope-2.0.0-blue)](https://github.com/agentscope-io/agentscope-java)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**JAgentFramework** 是面向 Java 生态的通用 AI Agent 编排框架。在底层 Agent 引擎（AgentScope）之上提供统一抽象层，赋予 **多模型路由、Skill 动态评分、拦截器链、五维评测、自动优化建议** 等企业级能力。

> **不是又一个 Agent 实现，而是管理 Agent 的框架。**

---

## 特性

| 能力 | 说明 |
|------|------|
| **多模型协作** | `Router` + `ModelRegistry` 支持 `provider:model` 动态路由，Pipeline / Parallel 编排 |
| **统一拦截器链** | `AgentInterceptor` / `SkillInterceptor` 三层 AOP，支持 before / after / around / onError |
| **Skill 动态评分** | 按 Agent 配置优先级权重，结合运行时成功率自动评分排序 |
| **GOAP 规划** | Goal-Plan-Step 模型，支持动态规划与重评估 |
| **五维评测系统** | 智能 / 性能 / 可靠性 / 安全 / 体验，支持规则评测 + LLM 评测 + 自定义 Agent 评测 |
| **置信度阈值与自动优化** | 低于阈值自动触发 LLM 分析，生成 Prompt / Skill / 模型 / 编排四维优化建议 |
| **AgentResponse 统一响应** | 一行返回业务数据 + trace + tokenUsage + evaluation + optimization |
| **Spring Boot 自动装配** | `@Import(JAgentAutoConfiguration.class)` 一键启用，YAML 配置驱动 |
| **Micrometer 监控** | 自动采集 Agent 执行耗时、Skill 调用次数、Token 消耗 |
| **SSE 流式输出** | 基于虚拟线程的 SSE 流式推送，前端 Markdown 实时渲染 |
| **OpenAI 兼容模型** | `OpenAICompatibleModel` 支持任意 OpenAI 协议端点（TokenPay、OneAPI 等） |
| **联网搜索多源 Fallback** | `WebSearchSkill` 依次尝试 Bing → DuckDuckGo → 360，自动降级 |
| **知识库检索** | `KnowledgeSearchSkill` 关键词匹配检索，Agent 优先查知识库再决定是否联网 |

---

## 架构

```
┌──────────────────────────────────────────────────┐
│                  jagent-demo                     │
│  Web 聊天 · 智能打标 · 翻译 · 摘要 · 评测 API    │
├──────────────────────────────────────────────────┤
│               jagent-agentscope                  │
│  Agent 适配 · Model 桥接 · Skill 桥接 · 自动装配  │
├──────────────────────────────────────────────────┤
│                 jagent-core                      │
│  agent · skill · model · plan · evaluation       │
│  prompt · router · feedback · monitor · task     │
└──────────────────────────────────────────────────┘
```

- **jagent-core** — 纯抽象层，仅依赖 JDK + SLF4J
- **jagent-agentscope** — 实现层，桥接 AgentScope 2.0 + Spring Boot
- **jagent-demo** — 业务示例，含 Web 聊天界面和完整打标流程

---

## 快速开始

### 环境

- Java 21+（推荐 25）
- Gradle 9.x

### 1. 引入依赖

```groovy
dependencies {
    implementation 'com.jrl.ai:jagent-agentscope:0.5.0'
}
```

> 需要配置 GitHub Packages 仓库，参见 [完整配置参考](#完整配置参考) 中的仓库设置。

### 2. 配置 application.yml

```yaml
jagent:
  workspace: "./workspace"
  model:
    api-keys:
      openai: ${OPENAI_API_KEY}
    base-urls:
      openai: https://your-openai-compatible-endpoint/v1
  agents:
    chat:
      name: "智能助手"
      sys-prompt: |
        你是一个智能助手，可以使用「知识检索」工具查询知识库。
        回答要求：简洁直接，每个要点独占一行，段落间用空行分隔。
      model: "openai:qwen3.8-max-preview"
      max-iters: 3
      session-enabled: true
      memory-enabled: true
      skill-priorities:
        knowledge_search: 0.9
    chat-web:
      name: "联网搜索助手"
      sys-prompt: |
        你是一个联网搜索助手。收到问题后调用 web_search 搜索，
        基于搜索结果回答，末尾附上参考来源 [标题](URL)。
      model: "openai:qwen3.8-max-preview"
      max-iters: 5
      session-enabled: true
      skill-priorities:
        web_search: 1.0
  evaluation:
    enabled: true
    weights:
      intelligence: 0.3
      performance: 0.15
      reliability: 0.2
      safety: 0.2
      experience: 0.15
    optimization:
      enabled: true
      confidence-threshold: 0.6
```

### 3. 启动 & 调用

```java
@SpringBootApplication
@Import(JAgentAutoConfiguration.class)
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

**同步调用：**
```java
@PostMapping("/chat")
public Mono<AgentResponse<String>> chat(@RequestBody ChatRequest req) {
    return Mono.fromCallable(() ->
        agentExecutor.execute("chat",
            ChatMessage.user(req.text()),
            AgentContext.builder().sessionId(req.sessionId()).build(),
            tr -> (String) tr.result().get("response"))
    ).subscribeOn(Schedulers.boundedElastic());
}
```

**SSE 流式输出：**
```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestBody ChatRequest req) {
    return agentService.stream("chat", req.text(), req.sessionId(), "user");
}
```

`AgentResponse<T>` 自动封装 trace、tokenUsage、评测结果和优化建议，无需手动处理。

---

## 核心概念

### 双 Agent 分层响应

```
用户提问 → chat（知识库检索）→ 回答
                ↓ 用户点「联网搜索」
         chat-web（联网搜索）→ 回答 + 参考来源
```

- **chat**：优先查知识库（`knowledge_search`），不自动联网
- **chat-web**：调用 `web_search` 搜索（Bing/DuckDuckGo/360 多源 fallback），必须附参考来源

### 拦截器链

```
用户输入 → beforeExecute → aroundExecute → Agent 推理 → afterExecute → 返回
                                    ↓
                                 onError（异常时）
```

### Skill 评分

```
最终评分 = 配置优先级 × 0.4 + 动态成功率 × 0.6
```

```java
@Bean
public Skill mySkill() {
    return new Skill() {
        @Override public String name() { return "web_search"; }
        @Override public String description() { return "联网搜索工具"; }
        @Override public SkillResult execute(SkillContext ctx) {
            // 搜索逻辑...
            return SkillResult.success("web_search", result, duration);
        }
    };
}
```

注册后自动通过 `SkillToolAdapter` 桥接为 AgentScope Tool，挂载到 Agent 的 Toolkit。

### 五维评测

| 维度 | 说明 | 方式 |
|------|------|------|
| Intelligence | 输出质量、相关性 | LLM 评测 |
| Performance | 延迟、Token 消耗 | 规则评测 |
| Reliability | 成功率、一致性 | 规则评测 |
| Safety | 内容安全、Prompt 泄露 | 规则 + LLM |
| Experience | 用户满意度 | 人工反馈 |

低于置信度阈值时，自动触发 LLM 优化分析，从 **Prompt / Skill / Model / Agent 编排** 四个维度生成改进建议。

---

## Demo 项目

`jagent-demo` 包含 Web 聊天界面和完整的智能打标示例：

```bash
export OPENAI_API_KEY=sk-xxx
./gradlew :jagent-demo:bootRun
```

启动后访问 `http://localhost:8080` 即可使用聊天界面，支持：

- **多轮对话** — 会话持久化 + 记忆能力
- **知识库检索** — chat Agent 优先查知识库，不自动联网
- **SSE 流式输出** — 虚拟线程驱动，文本增量实时推送
- **Markdown 渲染** — 前端 marked.js 实时解析，支持列表/代码块/链接
- **一键联网搜索** — 点击按钮触发 chat-web Agent，多源 fallback（Bing → DDG → 360）
- **参考来源链接** — 联网搜索结果自动附 `[标题](URL)` 格式来源
- **实时评测** — 置信度评分 + 低分自动优化建议

```bash
# 同步对话
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"agentKey":"chat","text":"介绍一下 JAgentFramework","sessionId":"s1"}'

# 智能打标
curl -X POST http://localhost:8080/api/tagging/tag \
  -H "Content-Type: application/json" \
  -d '{"contentId":"001","contentType":"product","contentText":"复古胶片风相机","requiredTagCount":5}'

# 查看评测结果
curl "http://localhost:8080/api/chat/evaluation?sessionId=s1"
```

---

## 完整配置参考

### 仓库配置

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

### 全量 YAML

```yaml
jagent:
  workspace: "./workspace"
  model:
    api-keys:
      openai: ${OPENAI_API_KEY}
    base-urls:
      openai: https://your-endpoint/v1
  agents:
    translator:
      name: "翻译助手"
      sys-prompt: "你是中英互译助手，只输出翻译结果"
      model: "openai:qwen3.8-max-preview"
      max-iters: 3
      max-retries: 3
    summarizer:
      name: "摘要助手"
      sys-prompt: "用简洁语言概括核心要点，不超过 100 字"
      model: "openai:qwen3.8-max-preview"
    chat:
      name: "智能助手"
      sys-prompt: |
        你是智能助手，可使用「知识检索」工具查询知识库。
        回答要求：简洁直接，每个要点独占一行，段落间用空行分隔。
      model: "openai:qwen3.8-max-preview"
      max-iters: 3
      session-enabled: true
      memory-enabled: true
      enable-search: false
      skill-priorities:
        knowledge_search: 0.9
    chat-web:
      name: "联网搜索助手"
      sys-prompt: |
        你是联网搜索助手。调用 web_search 搜索后基于结果回答，
        末尾附参考来源，格式：[标题](URL)
      model: "openai:qwen3.8-max-preview"
      max-iters: 5
      session-enabled: true
      skill-priorities:
        web_search: 1.0
    tagger:
      name: "智能打标助手"
      sys-prompt: "你是专业标签抽取引擎，输出 JSON 格式"
      model: "openai:qwen3.8-max-preview"
      skill-priorities:
        vector_search: 0.9
        vector_upsert: 0.7
        vector_get: 0.5
  evaluation:
    enabled: true
    llm-judge-enabled: true
    llm-judge-model: "openai:qwen3.8-max-preview"
    latency-threshold-ms: 10000
    weights:
      intelligence: 0.3
      performance: 0.15
      reliability: 0.2
      safety: 0.2
      experience: 0.15
    optimization:
      enabled: true
      llm-enabled: true
      llm-model: "openai:qwen3.8-max-preview"
      confidence-threshold: 0.6
```

---

## 构建

```bash
./gradlew compileJava          # 编译
./gradlew test                 # 测试
./gradlew build                # 全量构建
./gradlew publishToMavenLocal  # 发布到本地
```

发布到 GitHub Packages 需配置 `GITHUB_ACTOR` 和 `GITHUB_TOKEN` 环境变量，或通过创建 GitHub Release（如 `v0.5.0`）触发自动发布。

---

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 25（最低 21） |
| Spring Boot | 3.4.7 |
| AgentScope | 2.0.0 |
| Micrometer | 1.14.4 |
| Gradle | 9.2.0 |

---

## License

Apache License 2.0
