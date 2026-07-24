package com.jrl.ai.agent.core.agent;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;

/**
 * Agent — 框架的核心抽象
 */
public interface Agent {

    /**
     * Agent 唯一标识
     */
    String id();

    /**
     * Agent 名称
     */
    String name();

    /**
     * 同步执行
     */
    TaskResult execute(ChatMessage input, AgentContext context);

    /**
     * 是否支持流式输出
     */
    default boolean supportsStreaming() {
        return false;
    }
}
