package com.jrl.ai.agent.agentscope.agent;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import reactor.core.publisher.Flux;

/**
 * 流式 Agent 拦截器 — 包装流式执行，实现 AOP 统一抽象。
 *
 * <p>拦截器可在流式执行前后插入逻辑（如评测收集输出）。
 * 默认实现直接透传流。
 */
public interface StreamAgentInterceptor {

    /**
     * 流式执行环绕通知 — 可控制是否继续执行下游链路。
     *
     * <p>默认实现直接调用 {@code chain.proceed()}。
     *
     * @param agent   目标 Agent
     * @param input   用户输入消息
     * @param context 运行时上下文
     * @param chain   执行链，调用 {@code proceed()} 继续执行
     * @return 文本增量流
     */
    default Flux<String> aroundStream(Agent agent, ChatMessage input, AgentContext context,
                                       StreamExecutionChain chain) {
        return chain.proceed(input, context);
    }

    /**
     * 流式执行完成后的回调（流结束后触发）。
     *
     * <p>可用于收集完整输出执行评测等后处理。
     *
     * @param agent        目标 Agent
     * @param input        用户输入消息
     * @param context      运行时上下文
     * @param fullOutput   完整输出文本
     */
    default void onStreamComplete(Agent agent, ChatMessage input, AgentContext context, String fullOutput) {}

    /**
     * 流式执行链抽象 — 代表拦截器链中的下一个环节。
     */
    @FunctionalInterface
    interface StreamExecutionChain {
        /**
         * 继续执行下游链路。
         *
         * @param input   用户输入消息
         * @param context 运行时上下文
         * @return 文本增量流
         */
        Flux<String> proceed(ChatMessage input, AgentContext context);
    }
}
