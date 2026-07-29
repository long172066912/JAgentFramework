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
        /** 各 provider 的自定义 Base URL（可选，用于 API 代理） */
        private Map<String, String> baseUrls = new LinkedHashMap<>();

        public Map<String, String> getApiKeys() { return apiKeys; }
        public void setApiKeys(Map<String, String> apiKeys) { this.apiKeys = apiKeys; }

        public Map<String, String> getBaseUrls() { return baseUrls; }
        public void setBaseUrls(Map<String, String> baseUrls) { this.baseUrls = baseUrls; }
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
        /** LLM 评测 Prompt 模板（可选，使用 %s 占位符分别替换用户输入和 AI 输出） */
        private String llmJudgePrompt;
        /** 性能阈值（ms） */
        private long latencyThresholdMs = 10000;
        /** 五维权重 */
        private Map<String, Double> weights = new LinkedHashMap<>();
        /** 优化分析配置 */
        private OptimizationConfig optimization = new OptimizationConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isLlmJudgeEnabled() { return llmJudgeEnabled; }
        public void setLlmJudgeEnabled(boolean llmJudgeEnabled) { this.llmJudgeEnabled = llmJudgeEnabled; }

        public String getLlmJudgeModel() { return llmJudgeModel; }
        public void setLlmJudgeModel(String llmJudgeModel) { this.llmJudgeModel = llmJudgeModel; }

        public String getLlmJudgePrompt() { return llmJudgePrompt; }
        public void setLlmJudgePrompt(String llmJudgePrompt) { this.llmJudgePrompt = llmJudgePrompt; }

        public long getLatencyThresholdMs() { return latencyThresholdMs; }
        public void setLatencyThresholdMs(long latencyThresholdMs) { this.latencyThresholdMs = latencyThresholdMs; }

        public Map<String, Double> getWeights() { return weights; }
        public void setWeights(Map<String, Double> weights) { this.weights = weights; }

        public OptimizationConfig getOptimization() { return optimization; }
        public void setOptimization(OptimizationConfig optimization) { this.optimization = optimization; }
    }

    /**
     * 优化分析配置。
     */
    public static class OptimizationConfig {
        /** 是否启用 LLM 优化分析（默认 false，使用规则分析器） */
        private boolean llmEnabled = false;
        /** 置信度阈值，低于此分数时自动触发优化建议（默认 0.8） */
        private double confidenceThreshold = 0.8;

        public boolean isLlmEnabled() { return llmEnabled; }
        public void setLlmEnabled(boolean llmEnabled) { this.llmEnabled = llmEnabled; }

        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
    }

    /**
     * 单个 Agent 的配置。
     */
    public static class AgentConfig {
        /** Agent 显示名称 */
        private String name;
        /** 系统提示词（角色定义，静态） */
        private String sysPrompt = "You are a helpful AI assistant.";
        /** 用户提示词模板（支持 {variable} 占位符，每次请求动态渲染） */
        private String userPromptTemplate;
        /** 模型引用（格式: "provider:model"，如 "dashscope:qwen-plus"） */
        private String model = "dashscope:qwen-plus";
        /** 最大推理迭代次数 */
        private int maxIters = 20;
        /** 最大重试次数 */
        private int maxRetries = 3;
        /** Skill 优先级配置：skillName -> 基础分 (0.0~1.0) */
        private Map<String, Double> skillPriorities = new LinkedHashMap<>();
        /** 是否启用会话持久化（默认 false，单次任务无需保持会话历史） */
        private boolean sessionEnabled = false;
        /** 是否启用记忆工具（默认 false，单次任务无需记忆读写） */
        private boolean memoryEnabled = false;
        /** 是否启用 DashScope 联网搜索（默认 false，仅 dashscope provider 支持） */
        private boolean enableSearch = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSysPrompt() { return sysPrompt; }
        public void setSysPrompt(String sysPrompt) { this.sysPrompt = sysPrompt; }

        public String getUserPromptTemplate() { return userPromptTemplate; }
        public void setUserPromptTemplate(String userPromptTemplate) { this.userPromptTemplate = userPromptTemplate; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getMaxIters() { return maxIters; }
        public void setMaxIters(int maxIters) { this.maxIters = maxIters; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public Map<String, Double> getSkillPriorities() { return skillPriorities; }
        public void setSkillPriorities(Map<String, Double> skillPriorities) { this.skillPriorities = skillPriorities; }

        public boolean isSessionEnabled() { return sessionEnabled; }
        public void setSessionEnabled(boolean sessionEnabled) { this.sessionEnabled = sessionEnabled; }

        public boolean isMemoryEnabled() { return memoryEnabled; }
        public void setMemoryEnabled(boolean memoryEnabled) { this.memoryEnabled = memoryEnabled; }

        public boolean isEnableSearch() { return enableSearch; }
        public void setEnableSearch(boolean enableSearch) { this.enableSearch = enableSearch; }

        /**
         * 渲染用户提示词模板，替换 {variable} 占位符。
         *
         * @param variables 变量映射（key 为占位符名称，value 为替换值）
         * @return 渲染后的提示词，若无模板则返回 null
         */
        public String renderUserPrompt(Map<String, Object> variables) {
            if (userPromptTemplate == null || userPromptTemplate.isEmpty()) {
                return null;
            }
            String result = userPromptTemplate;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", 
                        entry.getValue() != null ? entry.getValue().toString() : "");
            }
            return result;
        }
    }
}
