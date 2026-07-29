package com.jrl.ai.agent.demo.chat.skill;

import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识检索 Skill — 模拟知识库检索（写死数据）。
 *
 * <p>根据关键词匹配返回预置的知识片段，模拟 RAG 检索效果。
 * 生产环境可替换为向量数据库检索实现。
 */
public class KnowledgeSearchSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchSkill.class);

    /** 模拟知识库：关键词 → 知识内容 */
    private static final Map<String, String> KNOWLEDGE_BASE = new LinkedHashMap<>();

    static {
        KNOWLEDGE_BASE.put("JAgent", """
                JAgentFramework 是一个面向 Java 生态的通用 AI Agent 编排框架。
                核心特性：多模型协作、统一拦截器链、Skill 动态评分、GOAP 规划、五维评测系统。
                技术栈：Java 25 + Spring Boot 3.4.7 + AgentScope 2.0。
                模块结构：jagent-core（纯抽象层）、jagent-agentscope（适配层）、jagent-demo（业务示例）。
                """);

        KNOWLEDGE_BASE.put("AgentScope", """
                AgentScope 是一个 Java 原生的 AI Agent 开发框架，当前版本 2.0.0。
                核心组件：HarnessAgent（Agent 执行引擎）、RuntimeContext（运行时上下文）、
                Toolkit（工具集）、ChatModel（对话模型抽象）。
                HarnessAgent 支持多轮推理、工具调用、流式输出、会话持久化等能力。
                """);

        KNOWLEDGE_BASE.put("Skill", """
                Skill 是 JAgentFramework 的可扩展能力单元。
                每个 Skill 包含：name（唯一标识）、description（描述，供 LLM 自主选择）、execute（执行逻辑）。
                Skill 通过 SkillRegistry 注册，自动桥接为 AgentScope 的 AgentTool，
                挂载到 HarnessAgent 的 Toolkit 中，LLM 推理时可自主发现并调用。
                支持按 Agent 维度配置优先级权重，结合运行时成功率动态评分。
                """);

        KNOWLEDGE_BASE.put("评测", """
                JAgentFramework 提供五维评测系统：智能、性能、可靠性、安全、体验。
                支持三层评测：Tier1 规则评测（零成本）、Tier2 LLM 评测（调用大模型打分）、
                Tier3 人工反馈（用户评分）。
                评测结果自动合并到 Agent 主链路 trace，全程可观测。
                低于置信度阈值时自动触发优化建议分析。
                """);

        KNOWLEDGE_BASE.put("Spring", """
                JAgentFramework 通过 JAgentAutoConfiguration 实现 Spring Boot 自动装配。
                只需 @Import(JAgentAutoConfiguration.class) 即可启用所有 Bean。
                配置通过 application.yml 的 jagent.* 前缀驱动，支持 Agent 声明、模型配置、
                评测开关、Skill 优先级等。
                """);

        KNOWLEDGE_BASE.put("模型", """
                JAgentFramework 支持多模型路由，模型引用格式为 provider:modelName。
                当前支持的 provider：dashscope（通义千问系列）、openai（OpenAI 兼容 API）。
                通过 ModelRegistry 注册和解析模型，Router 负责按任务选择最优模型。
                示例：dashscope:qwen3.7-flash（快速免费）、dashscope:qwen-plus（高质量）。
                """);
    }

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "知识检索工具。根据用户问题中的关键词检索相关知识，辅助回答。" +
               "输入参数：query（检索关键词，1-3个关键词用空格分隔）。" +
               "可检索的知识领域：JAgent框架介绍、AgentScope、Skill机制、评测系统、Spring Boot集成、模型路由。";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        long start = System.currentTimeMillis();

        String query = (String) context.parameters().getOrDefault("query", "");
        if (query.isBlank()) {
            // 尝试从 input 中获取
            query = context.input() != null ? context.input() : "";
        }

        log.info("[KnowledgeSearch] query='{}'", query);

        // 关键词匹配（简单实现，生产环境应使用向量检索）
        StringBuilder results = new StringBuilder();
        String lowerQuery = query.toLowerCase();

        for (Map.Entry<String, String> entry : KNOWLEDGE_BASE.entrySet()) {
            String keyword = entry.getKey().toLowerCase();
            String content = entry.getValue();

            // 关键词匹配：query 包含知识库关键词，或知识库关键词包含 query 中的词
            boolean match = lowerQuery.contains(keyword) || keyword.contains(lowerQuery);
            if (!match) {
                // 分词匹配：query 中任意词匹配
                for (String word : lowerQuery.split("\\s+")) {
                    if (word.length() >= 2 && (keyword.contains(word) || content.toLowerCase().contains(word))) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                if (!results.isEmpty()) {
                    results.append("\n---\n");
                }
                results.append("【").append(entry.getKey()).append("】\n").append(content);
            }
        }

        String output = results.isEmpty()
                ? "未找到相关知识，请基于自身知识回答。"
                : results.toString();

        long duration = System.currentTimeMillis() - start;
        log.info("[KnowledgeSearch] query='{}' matched={} duration={}ms",
                query, results.isEmpty() ? 0 : results.toString().split("---").length, duration);

        return SkillResult.success(name(), output, duration);
    }
}
