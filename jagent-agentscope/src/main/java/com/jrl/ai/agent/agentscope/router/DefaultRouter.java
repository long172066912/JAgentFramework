package com.jrl.ai.agent.agentscope.router;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.router.Router;
import com.jrl.ai.agent.core.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 默认路由器 — 基于 Task payload 中的 agentKey 字段进行路由。
 *
 * <p>路由策略：
 * <ol>
 *   <li>从 {@code task.request().payload()} 中提取 "agentKey" 字段</li>
 *   <li>通过 {@link AgentRegistry} 查找对应 Agent</li>
 *   <li>若无 agentKey 或找不到，fallback 到第一个已注册 Agent</li>
 * </ol>
 */
public class DefaultRouter implements Router {

    private static final Logger log = LoggerFactory.getLogger(DefaultRouter.class);

    private final AgentRegistry agentRegistry;

    /**
     * 创建默认路由器。
     *
     * @param agentRegistry Agent 注册表
     */
    public DefaultRouter(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Override
    public Agent route(Task task, AgentContext context) {
        // 从 payload 中提取 agentKey
        Map<String, Object> payload = task.request().payload();
        Object agentKeyObj = payload.get("agentKey");

        if (agentKeyObj != null) {
            String agentKey = agentKeyObj.toString();
            var agent = agentRegistry.get(agentKey);
            if (agent.isPresent()) {
                log.debug("路由到指定 Agent: {} -> {}", task.id(), agentKey);
                return agent.get();
            }
            log.warn("指定的 Agent 不存在: {}, fallback 到默认", agentKey);
        }

        // fallback: 取第一个已注册 Agent
        var allAgents = agentRegistry.all();
        if (allAgents.isEmpty()) {
            throw new IllegalStateException("没有已注册的 Agent 可供路由");
        }
        Agent fallback = allAgents.iterator().next();
        log.debug("路由到默认 Agent: {} -> {}", task.id(), fallback.id());
        return fallback;
    }

    @Override
    public String name() {
        return "default";
    }
}
