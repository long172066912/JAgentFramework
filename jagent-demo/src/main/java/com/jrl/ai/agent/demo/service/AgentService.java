package com.jrl.ai.agent.demo.service;

import com.jrl.ai.agent.agentscope.adapter.MessageConverter;
import com.jrl.ai.agent.agentscope.config.AgentFactory;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.TaskResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 业务服务 — 封装 Agent 调用逻辑，提供同步和流式两种调用方式。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentFactory agentFactory;

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * 同步对话 — 调用指定 Agent 并返回完整结果。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 任务结果
     */
    public TaskResult chat(String agentKey, String text, String sessionId, String userId) {
        Agent agent = agentFactory.getAgent(agentKey);
        AgentContext context = AgentContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
        return agent.execute(ChatMessage.user(text), context);
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
