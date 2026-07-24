package com.jrl.ai.agent.core.task.contract;

/**
 * Agent 错误码 — 标准化的错误分类
 */
public final class AgentErrorCode {

    private AgentErrorCode() {}

    // ========== 任务相关 ==========
    /** 任务参数无效 */
    public static final String INVALID_TASK = "INVALID_TASK";
    /** 任务超时 */
    public static final String TASK_TIMEOUT = "TASK_TIMEOUT";
    /** 任务被取消 */
    public static final String TASK_CANCELLED = "TASK_CANCELLED";
    /** 重复任务 */
    public static final String DUPLICATE_TASK = "DUPLICATE_TASK";

    // ========== 模型相关 ==========
    /** 模型不可用 */
    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    /** 模型配额不足 */
    public static final String MODEL_QUOTA_EXCEEDED = "MODEL_QUOTA_EXCEEDED";
    /** 模型响应格式异常 */
    public static final String MODEL_RESPONSE_INVALID = "MODEL_RESPONSE_INVALID";
    /** 模型限流 */
    public static final String MODEL_RATE_LIMITED = "MODEL_RATE_LIMITED";

    // ========== Skill/工具相关 ==========
    /** Skill 不存在 */
    public static final String SKILL_NOT_FOUND = "SKILL_NOT_FOUND";
    /** Skill 执行失败 */
    public static final String SKILL_EXECUTION_FAILED = "SKILL_EXECUTION_FAILED";
    /** 工具调用失败 */
    public static final String TOOL_CALL_FAILED = "TOOL_CALL_FAILED";

    // ========== 提示词相关 ==========
    /** 提示词模板不存在 */
    public static final String PROMPT_TEMPLATE_NOT_FOUND = "PROMPT_TEMPLATE_NOT_FOUND";
    /** 提示词渲染失败 */
    public static final String PROMPT_RENDER_FAILED = "PROMPT_RENDER_FAILED";

    // ========== 系统相关 ==========
    /** 内部错误 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /** 存储不可用 */
    public static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
    /** 记忆写入失败 */
    public static final String MEMORY_WRITE_FAILED = "MEMORY_WRITE_FAILED";
}
