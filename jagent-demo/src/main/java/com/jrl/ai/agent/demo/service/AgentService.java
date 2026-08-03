package com.jrl.ai.agent.demo.service;

import com.jrl.ai.agent.agentscope.adapter.AgentScopeAgentAdapter;
import com.jrl.ai.agent.agentscope.async.AsyncTaskManager;
import com.jrl.ai.agent.agentscope.config.AgentExecutor;
import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 业务服务 — 纯薄代理，所有执行逻辑由 AgentExecutor 统一处理。
 *
 * <p>评测由拦截器（AOP）自动处理，业务层无需关心。
 * <p>流式能力通过 Agent 原生的 streamEvents() 实现，利用虚拟线程异步调度。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentExecutor agentExecutor;
    private final AsyncTaskManager asyncTaskManager;

    public AgentService(AgentExecutor agentExecutor, AsyncTaskManager asyncTaskManager) {
        this.agentExecutor = agentExecutor;
        this.asyncTaskManager = asyncTaskManager;
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
     * 流式对话 — 返回文本增量流。
     *
     * <p>框架层通过回调通知文本增量事件，demo 层通过 Flux.create() 桥接回调到响应式流，
     * 实现真正的流式推送。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return 文本增量流
     */
    public Flux<String> stream(String agentKey, String text, String sessionId, String userId) {
        Agent agent = agentExecutor.getAgentFactory().getAgent(agentKey);
        if (!(agent instanceof AgentScopeAgentAdapter adapter)) {
            return Flux.error(new UnsupportedOperationException(
                    "Agent " + agentKey + " does not support streaming"));
        }

        AgentContext context = AgentContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        // 在 demo 层创建 Flux，将框架层的回调通知桥接到响应式流
        return Flux.create(sink -> {
            log.info("[Stream] Starting stream for agent={}, sessionId={}", agentKey, sessionId);
            adapter.streamEvents(
                    ChatMessage.user(text),
                    context,
                    sink::next,
                    sink::complete,
                    sink::error
            );
        });
    }

    /**
     * 异步执行任务 — 立即返回 taskId，支持短连接。
     *
     * <p>任务在后台异步执行，客户端可通过 taskId 查询状态或订阅事件流。
     *
     * @param agentKey  Agent 标识
     * @param text      用户输入文本
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @return taskId 任务标识
     */
    public String executeAsync(String agentKey, String text, String sessionId, String userId) {
        Agent agent = agentExecutor.getAgentFactory().getAgent(agentKey);
        if (!(agent instanceof AgentScopeAgentAdapter adapter)) {
            throw new UnsupportedOperationException(
                    "Agent " + agentKey + " does not support async execution");
        }

        AgentContext context = AgentContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        String taskId = asyncTaskManager.execute(adapter, ChatMessage.user(text), context);
        log.info("[Async] Task {} submitted for agent={}", taskId, agentKey);
        return taskId;
    }

    /**
     * 确认异步任务并恢复执行。
     *
     * @param taskId    任务标识
     * @param confirmed 是否确认
     * @return 任务信息
     */
    public AsyncTaskManager.TaskInfo confirmTask(String taskId, boolean confirmed) {
        return asyncTaskManager.confirmAndResume(taskId, confirmed);
    }

    /**
     * 获取异步任务信息。
     */
    public AsyncTaskManager.TaskInfo getTaskInfo(String taskId) {
        return asyncTaskManager.getTaskInfo(taskId);
    }

    /**
     * 订阅异步任务事件流（用于 SSE 推送）。
     */
    public Flux<AsyncTaskManager.TaskEvent> subscribeTask(String taskId) {
        return asyncTaskManager.subscribe(taskId);
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
