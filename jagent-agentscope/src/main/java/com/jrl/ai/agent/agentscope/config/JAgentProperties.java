package com.jrl.ai.agent.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JAgent 配置属性 — 绑定 {@code jagent.*} 前缀的 application.yml 配置。
 *
 * <p>示例配置：
 * <pre>{@code
 * jagent:
 *   model:
 *     api-keys:
 *       dashscope: "sk-xxx"
 *   agents:
 *     translator:
 *       name: "翻译助手"
 *       sys-prompt: "你是中英互译助手"
 *       model: "dashscope:qwen-plus"
 *   workspace: "./workspace"
 * }</pre>
 */
@ConfigurationProperties(prefix = "jagent")
public class JAgentProperties {

    /** Agent 声明列表，key 为 Agent 逻辑标识 */
    private Map<String, AgentConfig> agents = new LinkedHashMap<>();

    /** 全局工作空间路径 */
    private String workspace = "./workspace";

    /** 模型相关配置（API Key 等） */
    private ModelConfig model = new ModelConfig();

    /** 评测相关配置 */
    private EvaluationConfig evaluation = new EvaluationConfig();

    public Map<String, AgentConfig> getAgents() { return agents; }
    public void setAgents(Map<String, AgentConfig> agents) { this.agents = agents; }

    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }

    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }

    public EvaluationConfig getEvaluation() { return evaluation; }
    public void setEvaluation(EvaluationConfig evaluation) { this.evaluation = evaluation; }

    /**
     * 模型相关配置。
     */
    public static class ModelConfig {
        /** 各 provider 的 API Key，key 为 provider 标识（如 "dashscope"） */
        private Map<String, String> apiKeys = new LinkedHashMap<>();

        public Map<String, String> getApiKeys() { return apiKeys; }
        public void setApiKeys(Map<String, String> apiKeys) { this.apiKeys = apiKeys; }
    }

    /**
     * 评测相关配置。
     */
    public static class EvaluationConfig {
        /** 是否启用评测（默认 false） */
        private boolean enabled = false;
        /** 是否启用 LLM 评测（默认 false） */
        private boolean llmJudgeEnabled = false;
        /** LLM 评测使用的模型 */
        private String llmJudgeModel = "dashscope:qwen-plus";
        /** 性能阈值（ms） */
        private long latencyThresholdMs = 10000;
        /** 五维权重 */
        private Map<String, Double> weights = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isLlmJudgeEnabled() { return llmJudgeEnabled; }
        public void setLlmJudgeEnabled(boolean llmJudgeEnabled) { this.llmJudgeEnabled = llmJudgeEnabled; }

        public String getLlmJudgeModel() { return llmJudgeModel; }
        public void setLlmJudgeModel(String llmJudgeModel) { this.llmJudgeModel = llmJudgeModel; }

        public long getLatencyThresholdMs() { return latencyThresholdMs; }
        public void setLatencyThresholdMs(long latencyThresholdMs) { this.latencyThresholdMs = latencyThresholdMs; }

        public Map<String, Double> getWeights() { return weights; }
        public void setWeights(Map<String, Double> weights) { this.weights = weights; }
    }

    /**
     * 单个 Agent 的配置。
     */
    public static class AgentConfig {
        /** Agent 显示名称 */
        private String name;
        /** 系统提示词 */
        private String sysPrompt = "You are a helpful AI assistant.";
        /** 模型引用（格式: "provider:model"，如 "dashscope:qwen-plus"） */
        private String model = "dashscope:qwen-plus";
        /** 最大推理迭代次数 */
        private int maxIters = 20;
        /** 最大重试次数 */
        private int maxRetries = 3;
        /** Skill 优先级配置：skillName -> 基础分 (0.0~1.0) */
        private Map<String, Double> skillPriorities = new LinkedHashMap<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSysPrompt() { return sysPrompt; }
        public void setSysPrompt(String sysPrompt) { this.sysPrompt = sysPrompt; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getMaxIters() { return maxIters; }
        public void setMaxIters(int maxIters) { this.maxIters = maxIters; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public Map<String, Double> getSkillPriorities() { return skillPriorities; }
        public void setSkillPriorities(Map<String, Double> skillPriorities) { this.skillPriorities = skillPriorities; }
    }
}
