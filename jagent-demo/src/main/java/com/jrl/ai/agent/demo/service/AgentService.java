package com.jrl.ai.agent.demo.service;

import com.jrl.ai.agent.agentscope.config.AgentExecutor;
import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.AgentResponse;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 业务服务 — 使用 AgentExecutor 统一处理公共字段。
 *
 * <p>业务层只关心返回文本，trace/tokenUsage/evaluation/optimization 由框架自动处理。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentExecutor agentExecutor;
    private final AgentFactory agentFactory;

    public AgentService(AgentExecutor agentExecutor, AgentFactory agentFactory) {
        this.agentExecutor = agentExecutor;
        this.agentFactory = agentFactory;
    }

    /**
     * 同步对话 — 返回 AgentResponse，业务数据为响应文本。
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
     * 流式对话 — 返回 AgentScope 事件流，适合 SSE 推送。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return AgentEvent 事件流
     */
    public Flux<AgentEvent> stream(String agentKey, String text, String sessionId, String userId) {
        HarnessAgent harness = agentFactory.getHarnessAgent(agentKey);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
        return harness.streamEvents(new UserMessage(text));
    }

    /**
     * 列出所有已注册的 Agent 信息。
     *
     * @return Agent 标识 → 名称映射
     */
    public Map<String, String> listAgents() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : agentFactory.allAgentKeys()) {
            Agent agent = agentFactory.getAgent(key);
            result.put(key, agent.name());
        }
        return result;
    }
}
