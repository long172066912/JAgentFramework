package com.jrl.ai.agent.agentscope.agent;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import reactor.core.publisher.Flux;

/**
 * 流式 Agent — 扩展 Agent 接口，支持流式输出。
 *
 * <p>适配层（如 AgentScope）实现此接口以支持流式事件推送。
 * 拦截器链可同时包装同步和流式执行。
 */
public interface StreamingAgent extends Agent {

    /**
     * 流式执行 Agent — 返回文本增量流。
     *
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @return 文本增量流
     */
    Flux<String> stream(ChatMessage input, AgentContext context);

    @Override
    default boolean supportsStreaming() {
        return true;
    }
}
