package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentRegistry;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.mock.MockAgent;
import com.jrl.ai.agent.core.mock.MockModel;
import com.jrl.ai.agent.core.task.TaskResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 执行流程测试 — 验证核心调用链路。
 */
@DisplayName("Agent 执行流程测试")
class AgentExecutionTest {

    private MockModel model;
    private MockAgent agent;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        model = new MockModel("qwen-max", "dashscope")
                .withFixedResponse("你好，我是 AI 助手！");
        agent = new MockAgent("agent-001", "测试助手", model);
        context = AgentContext.builder()
                .sessionId("session-001")
                .userId("user-alice")
                .attribute("modelId", "qwen-max")
                .build();
    }

    @Test
    @DisplayName("基本执行：Agent 接收输入并返回结果")
    void testBasicExecution() {
        ChatMessage input = ChatMessage.user("你好");
        TaskResult result = agent.execute(input, context);

        assertTrue(result.isSuccess());
        assertEquals("task-agent-001", result.taskId());
        assertEquals("session-001", result.sessionId());
        assertNotNull(result.result());
        assertEquals("text", result.resultType());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    @DisplayName("模型调用：验证模型被正确调用")
    void testModelInvocation() {
        ChatMessage input = ChatMessage.user("测试消息");
        agent.execute(input, context);

        assertEquals(1, model.getCallCount());
    }

    @Test
    @DisplayName("Token 统计：验证 Token 用量记录")
    void testTokenUsage() {
        ChatMessage input = ChatMessage.user("统计测试");
        TaskResult result = agent.execute(input, context);

        assertNotNull(result.usage());
        assertEquals("qwen-max", result.usage().modelId());
        assertTrue(result.usage().totalTokens() > 0);
    }

    @Test
    @DisplayName("上下文透传：验证 sessionId/userId 正确传递")
    void testContextPropagation() {
        ChatMessage input = ChatMessage.user("上下文测试");
        TaskResult result = agent.execute(input, context);

        assertEquals(context.sessionId(), result.sessionId());
        assertEquals("user-alice", context.userId());
    }

    @Test
    @DisplayName("Agent 注册表：注册、查找、注销")
    void testAgentRegistry() {
        // 使用简单的内存注册表实现
        Map<String, Agent> store = new HashMap<>();
        AgentRegistry registry = new AgentRegistry() {
            @Override public void register(Agent a) { store.put(a.id(), a); }
            @Override public Optional<Agent> get(String id) { return Optional.ofNullable(store.get(id)); }
            @Override public void unregister(String id) { store.remove(id); }
            @Override public Collection<Agent> all() { return List.copyOf(store.values()); }
        };

        registry.register(agent);
        assertTrue(registry.get("agent-001").isPresent());
        assertEquals("测试助手", registry.get("agent-001").get().name());
        assertEquals(1, registry.all().size());

        registry.unregister("agent-001");
        assertTrue(registry.get("agent-001").isEmpty());
    }

    @Test
    @DisplayName("结果转换：TaskResult 转 TaskResponse")
    void testResultToResponse() {
        ChatMessage input = ChatMessage.user("转换测试");
        TaskResult result = agent.execute(input, context);

        var response = result.toResponse();
        assertNotNull(response);
        assertEquals("task-agent-001", response.taskId());
        assertTrue(response.processTime() >= 0);
    }
}
