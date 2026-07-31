/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 子 Agent 调度日志增强中间件。
 *
 * <p>当父 Agent 调用 {@code agent_spawn} 工具时，从工具参数中提取子 Agent ID，
 * 输出更清晰的日志，便于追踪子 Agent 调度链路。
 *
 * <p>日志格式：
 * <pre>
 * [父Agent] SPAWN → 子Agent(id=translator)
 * [父Agent] SPAWN_DONE → 子Agent(id=translator) | result_len=169, state=SUCCESS
 * </pre>
 */
public class SubagentTraceMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(SubagentTraceMiddleware.class);

    private static final String AGENT_SPAWN_TOOL = "agent_spawn";
    private static final String AGENT_ID_PARAM = "agent_id";

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {

        if (input.toolCalls() == null) {
            return next.apply(input);
        }

        // 检查是否有 agent_spawn 调用
        for (ToolUseBlock tu : input.toolCalls()) {
            if (AGENT_SPAWN_TOOL.equals(tu.getName())) {
                String agentId = extractAgentId(tu.getInput());
                String parentName = agent.getName();
                log.info("[{}] SPAWN → 子Agent(id={})", parentName, agentId);
            }
        }

        // 监听工具执行结果
        Map<String, String> spawnAgentIds = new ConcurrentHashMap<>();
        Map<String, StringBuilder> resultText = new ConcurrentHashMap<>();

        return next.apply(input)
                .doOnNext(ev -> {
                    if (ev instanceof ToolResultStartEvent start) {
                        if (AGENT_SPAWN_TOOL.equals(start.getToolCallName())) {
                            // 尝试从 input 中找到对应的 agent_id
                            for (ToolUseBlock tu : input.toolCalls()) {
                                if (tu.getId().equals(start.getToolCallId())) {
                                    String agentId = extractAgentId(tu.getInput());
                                    spawnAgentIds.put(start.getToolCallId(), agentId);
                                    break;
                                }
                            }
                        }
                    } else if (ev instanceof ToolResultTextDeltaEvent delta) {
                        if (delta.getDelta() != null) {
                            resultText.computeIfAbsent(delta.getToolCallId(), k -> new StringBuilder())
                                    .append(delta.getDelta());
                        }
                    } else if (ev instanceof ToolResultEndEvent end) {
                        String agentId = spawnAgentIds.get(end.getToolCallId());
                        if (agentId != null) {
                            String text = resultText.getOrDefault(end.getToolCallId(), new StringBuilder()).toString();
                            log.info("[{}] SPAWN_DONE → 子Agent(id={}) | result_len={}, state={}",
                                    agent.getName(), agentId, text.length(), end.getState());
                        }
                    }
                });
    }

    /**
     * 从 agent_spawn 工具的输入参数中提取 agent_id。
     */
    private String extractAgentId(Map<String, Object> input) {
        if (input == null) return "<unknown>";
        Object agentId = input.get(AGENT_ID_PARAM);
        return agentId != null ? agentId.toString() : "<unknown>";
    }
}
