package com.jrl.ai.agent.demo.service;

import com.jrl.ai.agent.agentscope.config.AgentExecutor;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.AgentResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 业务服务 — 纯薄代理，所有执行逻辑由 AgentExecutor 统一处理。
 *
 * <p>评测由拦截器（AOP）自动处理，业务层无需关心。
 */
@Service
public class AgentService {

    private final AgentExecutor agentExecutor;

    public AgentService(AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    /**
     * 同步对话 — 返回 AgentResponse，业务数据为响应文本。
     *
     * <p>评测由拦截器链自动处理。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return AgentResponse，data 为 Agent 响应文本
     */
    public AgentResponse<String> chat(String agentKey, String text, String sessionId, String userId) {
        AgentContext context = AgentContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        return agentExecutor.execute(
                agentKey,
                ChatMessage.user(text),
                context,
                taskResult -> (String) taskResult.result().getOrDefault("response", "")
        );
    }

    /**
     * 列出所有已注册的 Agent 信息。
     *
     * @return Agent 标识 → 名称映射
     */
    public Map<String, String> listAgents() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : agentExecutor.getAgentFactory().allAgentKeys()) {
            Agent agent = agentExecutor.getAgentFactory().getAgent(key);
            result.put(key, agent.name());
        }
        return result;
    }
}
