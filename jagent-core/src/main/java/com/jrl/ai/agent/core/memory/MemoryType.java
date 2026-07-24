package com.jrl.ai.agent.core.memory;

/**
 * 记忆类型
 */
public enum MemoryType {
    /** 短期记忆 — 当前会话 */
    SHORT_TERM,
    /** 长期记忆 — 跨会话持久化 */
    LONG_TERM,
    /** 工作记忆 — 当前任务上下文 */
    WORKING,
    /** 情景记忆 — 具体事件/经历 */
    EPISODIC,
    /** 语义记忆 — 事实/知识 */
    SEMANTIC
}
