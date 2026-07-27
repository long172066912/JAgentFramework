package com.jrl.ai.agent.core.task.contract;

import java.util.List;
import java.util.Map;

/**
 * 任务请求 — 与传输方式无关的任务输入契约
 * <p>
 * 无论是 MQ、HTTP、gRPC 还是 WebSocket，最终都转换为 TaskRequest 交给框架处理。
 * 传输层适配器负责将具体协议的消息转换为 TaskRequest。
 */
public record TaskRequest(
        /** 任务唯一标识 */
        String taskId,
        /** 任务类型（如 mark_tag, content_gen 等） */
        String taskType,
        /** 会话 ID — 关联 AgentContext.sessionId */
        String sessionId,
        /** 用户 ID — 关联 AgentContext.userId */
        String userId,
        /** 优先级：1紧急 2普通 3低 */
        int priority,
        /** 指定模型 ID（空则用默认模型） */
        String modelId,
        /** 提示词模板名称 — 关联 PromptRegistry */
        String promptTemplate,
        /** 提示词变量 — 用于渲染模板 */
        Map<String, Object> promptVariables,
        /** 可用 Skill 列表 — 关联 SkillRegistry（空则用全部可用 Skill） */
        List<String> skillNames,
        /** 超时时间 ms（0 表示不限制） */
        long timeoutMs,
        /** 重试次数（0 表示不重试） */
        int retryCount,
        /** 业务数据 */
        Map<String, Object> payload,
        /** AI 辅助说明 — 补充上下文给 Agent */
        String remark,
        /** 提交时间戳 */
        long timestamp
) {

    /** 紧急优先级 */
    public static final int PRIORITY_URGENT = 1;
    /** 普通优先级（默认） */
    public static final int PRIORITY_NORMAL = 2;
    /** 低优先级 */
    public static final int PRIORITY_LOW = 3;

    /**
     * 创建新的 Builder。
     *
     * @return 新的 TaskRequest 构建器
     */
    public static Builder builder() { return new Builder(); }

    /**
     * TaskRequest 构建器 — 支持流式 API。
     */
    public static class Builder {
        private String taskId;
        private String taskType;
        private String sessionId;
        private String userId;
        private int priority = PRIORITY_NORMAL;
        private String modelId;
        private String promptTemplate;
        private Map<String, Object> promptVariables = Map.of();
        private List<String> skillNames = List.of();
        private long timeoutMs = 300_000;
        private int retryCount = 0;
        private Map<String, Object> payload = Map.of();
        private String remark;
        private long timestamp = System.currentTimeMillis();

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder promptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; return this; }
        public Builder promptVariables(Map<String, Object> vars) { this.promptVariables = vars; return this; }
        public Builder skillNames(List<String> skillNames) { this.skillNames = skillNames; return this; }
        public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public Builder remark(String remark) { this.remark = remark; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }

        public TaskRequest build() {
            return new TaskRequest(
                    taskId, taskType, sessionId, userId, priority,
                    modelId, promptTemplate, promptVariables, skillNames,
                    timeoutMs, retryCount, payload, remark, timestamp
            );
        }
    }
}
