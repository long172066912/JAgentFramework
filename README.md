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

---

## 架构

```
┌──────────────────────────────────────────────┐
│              jagent-demo                     │
│         （聊天、打标、翻译、摘要）              │
├──────────────────────────────────────────────┤
│            jagent-agentscope                 │
│   Agent 适配 · Skill 桥接 · Spring 自动装配   │
├──────────────────────────────────────────────┤
│              jagent-core                     │
│  agent · skill · model · plan · evaluation   │
│  prompt · router · feedback · monitor · task │
└──────────────────────────────────────────────┘
```

- **jagent-core** — 纯抽象层，仅依赖 JDK + SLF4J
- **jagent-agentscope** — 实现层，桥接 AgentScope 2.0 + Spring Boot
- **jagent-demo** — 业务示例，含 Web 聊天界面

---

## 快速开始

### 环境

- Java 21+（推荐 25）
- Gradle 9.x

### 1. 引入依赖

```groovy
dependencies {
    implementation 'com.jrl.ai:jagent-agentscope:0.4.0'
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
        你是一个智能助手。
        回答要求：简洁直接，每个要点独占一行，段落间用空行分隔。
      model: "openai:qwen3.8-max-preview"
      max-iters: 3
      session-enabled: true
      memory-enabled: true
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

```java
@RestController
public class ChatController {

    private final AgentExecutor agentExecutor;

    @PostMapping("/chat")
    public Mono<AgentResponse<String>> chat(@RequestBody ChatRequest req) {
        return Mono.fromCallable(() ->
            agentExecutor.execute("chat",
                ChatMessage.user(req.text()),
                AgentContext.builder().sessionId(req.sessionId()).build(),
                tr -> (String) tr.result().get("response"))
        ).subscribeOn(Schedulers.boundedElastic());
    }
}
```

`AgentResponse<T>` 自动封装 trace、tokenUsage、评测结果和优化建议，无需手动处理。

---

## 核心概念

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
- 多轮对话 + 知识库检索
- SSE 流式输出 + Markdown 渲染
- 一键联网搜索（多源 fallback）
- 实时评测置信度展示
- 参考来源链接

```bash
# 智能打标
curl -X POST http://localhost:8080/api/tagging/tag \
  -H "Content-Type: application/json" \
  -d '{"contentId":"001","contentType":"product","contentText":"复古胶片风相机","requiredTagCount":5}'

# 查看 Skill 评分
curl http://localhost:8080/api/skill-scoring/scores/tagger
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
      sys-prompt: "你是中英互译助手"
      model: "openai:qwen3.8-max-preview"
      max-iters: 10
      max-retries: 3
      session-enabled: true
      memory-enabled: true
      skill-priorities:
        vector_search: 0.9
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

发布到 GitHub Packages 需配置 `GITHUB_ACTOR` 和 `GITHUB_TOKEN` 环境变量，或通过创建 GitHub Release 触发自动发布。

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
