package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.task.Task;
import com.jrl.ai.agent.core.task.TaskResult;
import com.jrl.ai.agent.core.task.TaskStatus;
import com.jrl.ai.agent.core.task.contract.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务契约完整流程测试 — 验证 TaskRequest → Task → TaskResult → TaskResponse 全链路。
 */
@DisplayName("任务契约完整流程测试")
class TaskContractTest {

    @Test
    @DisplayName("TaskRequest 构建：Builder 模式")
    void testTaskRequestBuilder() {
        TaskRequest request = TaskRequest.builder()
                .taskId("task-001")
                .taskType("translate")
                .sessionId("session-001")
                .userId("user-alice")
                .priority(TaskRequest.PRIORITY_URGENT)
                .modelId("qwen-max")
                .promptTemplate("translate-prompt")
                .promptVariables(Map.of("targetLang", "en"))
                .timeoutMs(60000)
                .build();

        assertEquals("task-001", request.taskId());
        assertEquals("translate", request.taskType());
        assertEquals(TaskRequest.PRIORITY_URGENT, request.priority());
        assertEquals("qwen-max", request.modelId());
    }

    @Test
    @DisplayName("Task 生命周期：PENDING → RUNNING → COMPLETED")
    void testTaskLifecycle() {
        TaskRequest request = TaskRequest.builder()
                .taskId("task-002")
                .taskType("content_gen")
                .sessionId("session-001")
                .userId("user-alice")
                .build();

        Task task = Task.fromRequest(request);
        assertEquals(TaskStatus.PENDING, task.status());
        assertEquals("task-002", task.id());

        Task running = task.start();
        assertEquals(TaskStatus.RUNNING, running.status());
        assertNotNull(running.startedAt());

        Task completed = running.complete();
        assertEquals(TaskStatus.COMPLETED, completed.status());
        assertNotNull(completed.completedAt());
        assertTrue(completed.durationMs() >= 0);
    }

    @Test
    @DisplayName("Task 失败：PENDING → RUNNING → FAILED")
    void testTaskFailure() {
        TaskRequest request = TaskRequest.builder()
                .taskId("task-003")
                .taskType("translate")
                .sessionId("session-001")
                .userId("user-alice")
                .build();

        Task task = Task.fromRequest(request).start().fail();
        assertEquals(TaskStatus.FAILED, task.status());
    }

    @Test
    @DisplayName("TaskResult 成功：创建并转换为 TaskResponse")
    void testTaskResultSuccess() {
        TaskResult result = TaskResult.success(
                "task-001", "session-001", "text",
                Map.of("content", "翻译结果"),
                TokenUsage.of(100, 50, "qwen-max"),
                250
        );

        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(250, result.durationMs());

        TaskResponse response = result.toResponse();
        assertEquals(ResponseStatus.SUCCESS, response.status());
        assertEquals(100, response.progress());
        assertNotNull(response.result());
    }

    @Test
    @DisplayName("TaskResult 失败：创建并转换为 TaskResponse")
    void testTaskResultFailure() {
        TaskResult result = TaskResult.failure(
                "task-002", "session-001",
                "MODEL_UNAVAILABLE", "模型服务不可用", 100
        );

        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.status());
        assertNotNull(result.error());

        TaskResponse response = result.toResponse();
        assertEquals(ResponseStatus.FAIL, response.status());
        assertNotNull(response.errorCode());
    }

    @Test
    @DisplayName("TaskResponse 状态：processing/success/failure/timeout")
    void testTaskResponseStatuses() {
        TaskResponse processing = TaskResponse.processing("t1", "s1", "translate", 50);
        assertEquals(ResponseStatus.PROCESSING, processing.status());
        assertEquals(50, processing.progress());

        TaskResponse success = TaskResponse.success("t1", "s1", "translate",
                "text", Map.of("ok", true), TokenUsage.of(10, 20, "m1"), 100);
        assertEquals(ResponseStatus.SUCCESS, success.status());

        TaskResponse failure = TaskResponse.failure("t1", "s1", "translate",
                "ERROR", "出错", 50);
        assertEquals(ResponseStatus.FAIL, failure.status());

        TaskResponse timeout = TaskResponse.timeout("t1", "s1", "translate", 30000);
        assertEquals(ResponseStatus.TIMEOUT, timeout.status());
        assertEquals(AgentErrorCode.TASK_TIMEOUT, timeout.errorCode());
    }

    @Test
    @DisplayName("TaskContractConverter：TaskRequest 转 Task 和 AgentContext")
    void testTaskContractConverter() {
        TaskRequest request = TaskRequest.builder()
                .taskId("task-004")
                .taskType("translate")
                .sessionId("session-001")
                .userId("user-alice")
                .modelId("qwen-max")
                .promptTemplate("my-prompt")
                .build();

        Task task = TaskContractConverter.toTask(request);
        assertEquals("task-004", task.id());
        assertEquals(TaskStatus.PENDING, task.status());

        var context = TaskContractConverter.toContext(request);
        assertEquals("session-001", context.sessionId());
        assertEquals("user-alice", context.userId());
        assertTrue(context.<String>get("modelId").isPresent());
    }

    @Test
    @DisplayName("TokenUsage：Token 统计计算")
    void testTokenUsage() {
        TokenUsage usage = TokenUsage.of(100, 50, "qwen-max");
        assertEquals(100, usage.promptTokens());
        assertEquals(50, usage.completionTokens());
        assertEquals(150, usage.totalTokens());
        assertEquals("qwen-max", usage.modelId());
    }

    @Test
    @DisplayName("AgentErrorCode：错误码常量验证")
    void testAgentErrorCodes() {
        assertNotNull(AgentErrorCode.MODEL_UNAVAILABLE);
        assertNotNull(AgentErrorCode.TASK_TIMEOUT);
        assertNotNull(AgentErrorCode.SKILL_EXECUTION_FAILED);
        assertNotNull(AgentErrorCode.INTERNAL_ERROR);
    }
}
