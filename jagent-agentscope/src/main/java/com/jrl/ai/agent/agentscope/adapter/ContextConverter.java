package com.jrl.ai.agent.agentscope.adapter;

import com.jrl.ai.agent.core.context.AgentContext;
import io.agentscope.core.agent.RuntimeContext;

/**
 * 上下文转换器 — jagent {@link AgentContext} 与 AgentScope {@link RuntimeContext} 的双向转换。
 *
 * <p>两个上下文对象都承载 sessionId 和 userId，
 * 但 AgentScope 的 RuntimeContext 额外支持 traceId 和泛型属性存取。
 */
public final class ContextConverter {

    private ContextConverter() {}

    /**
     * 将 jagent AgentContext 转换为 AgentScope RuntimeContext。
     *
     * @param context jagent 上下文
     * @return AgentScope 运行时上下文
     */
    public static RuntimeContext toAgentScope(AgentContext context) {
        if (context == null) return RuntimeContext.empty();
        var builder = RuntimeContext.builder()
                .sessionId(context.sessionId())
                .userId(context.userId());
        // 透传扩展属性
        context.attributes().forEach(builder::put);
        return builder.build();
    }

    /**
     * 将 AgentScope RuntimeContext 转换为 jagent AgentContext。
     *
     * @param ctx AgentScope 运行时上下文
     * @return jagent 上下文
     */
    public static AgentContext toJAgent(RuntimeContext ctx) {
        if (ctx == null) return AgentContext.builder().build();
        var builder = AgentContext.builder()
                .sessionId(ctx.getSessionId())
                .userId(ctx.getUserId());
        // 透传扩展属性
        ctx.getExtra().forEach(builder::attribute);
        return builder.build();
    }
}
