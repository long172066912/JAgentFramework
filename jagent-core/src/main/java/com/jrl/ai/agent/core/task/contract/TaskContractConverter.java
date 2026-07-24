package com.jrl.ai.agent.core.task.contract;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.task.Task;

import java.util.Map;

/**
 * 协议转换器 — 将 TaskRequest 转换为框架内部模型
 */
public final class TaskContractConverter {

    private TaskContractConverter() {}

    public static Task toTask(TaskRequest request) {
        return new Task(
                request.taskId(),
                request.taskType(),
                request.remark(),
                request.payload().toString(),
                com.jrl.ai.agent.core.task.TaskStatus.PENDING,
                java.time.Instant.ofEpochMilli(request.timestamp()),
                null,
                null,
                Map.of(
                        "priority", request.priority(),
                        "modelId", nullSafe(request.modelId()),
                        "promptTemplate", nullSafe(request.promptTemplate()),
                        "timeoutMs", request.timeoutMs(),
                        "retryCount", request.retryCount()
                )
        );
    }

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

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
