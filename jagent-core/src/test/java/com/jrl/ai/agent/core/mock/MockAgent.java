package com.jrl.ai.agent.core.mock;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.TaskStatus;
import com.jrl.ai.agent.core.task.contract.TokenUsage;

import java.util.List;
import java.util.Map;

/**
 * Mock Agent — 用于测试的模拟 Agent 实现。
 *
 * <p>内部使用 MockModel 生成响应，模拟完整的 Agent 执行流程。
 */
public class MockAgent implements Agent {

    private final String id;
    private final String name;
    private final Model model;

    public MockAgent(String id, String name, Model model) {
        this.id = id;
        this.name = name;
        this.model = model;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public TaskResult execute(ChatMessage input, AgentContext context) {
        // 构建对话消息
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是 " + name + "，一个测试用 Agent。"),
                input
        );

        // 调用模型
        long start = System.currentTimeMillis();
        String response = model.call(messages);
        long duration = System.currentTimeMillis() - start;

        // 构造结果
        return TaskResult.success(
                "task-" + id,
                context.sessionId(),
                "text",
                Map.of("response", response, "model", model.modelId()),
                TokenUsage.of(10, response.length(), model.modelId()),
                duration
        );
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }
}
