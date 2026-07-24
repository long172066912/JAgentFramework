package com.jrl.ai.agent.core.task.contract;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;

/**
 * 协议转换器 — TaskRequest ↔ Task 的转换
 */
public final class TaskContractConverter {

    private TaskContractConverter() {}

    /**
     * 将外部请求转为内部 Task
     */
    public static Task toTask(TaskRequest request) {
        return Task.fromRequest(request);
    }

    /**
     * 从请求中提取 AgentContext
     */
    public static AgentContext toContext(TaskRequest request) {
        var builder = AgentContext.builder()
                .sessionId(request.sessionId())
                .userId(request.userId());

        if (request.modelId() != null) {
            builder.attribute("modelId", request.modelId());
        }
        if (request.promptTemplate() != null) {
            builder.attribute("promptTemplate", request.promptTemplate());
        }
        if (!request.promptVariables().isEmpty()) {
            builder.attribute("promptVariables", request.promptVariables());
        }
        if (!request.skillNames().isEmpty()) {
            builder.attribute("skillNames", request.skillNames());
        }

        return builder.build();
    }
}
