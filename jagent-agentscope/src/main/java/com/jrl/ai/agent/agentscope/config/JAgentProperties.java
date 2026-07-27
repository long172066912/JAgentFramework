package com.jrl.ai.agent.agentscope.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JAgent 配置属性 — 绑定 {@code jagent.*} 前缀的 application.yml 配置。
 *
 * <p>示例配置：
 * <pre>{@code
 * jagent:
 *   agents:
 *     translator:
 *       name: "翻译助手"
 *       sys-prompt: "你是中英互译助手"
 *       model: "dashscope:qwen-plus"
 *   workspace: "./workspace"
 * }</pre>
 */
public class JAgentProperties {

    /** Agent 声明列表，key 为 Agent 逻辑标识 */
    private Map<String, AgentConfig> agents = new LinkedHashMap<>();

    /** 全局工作空间路径 */
    private String workspace = "./workspace";

    public Map<String, AgentConfig> getAgents() { return agents; }
    public void setAgents(Map<String, AgentConfig> agents) { this.agents = agents; }

    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }

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
    }
}
