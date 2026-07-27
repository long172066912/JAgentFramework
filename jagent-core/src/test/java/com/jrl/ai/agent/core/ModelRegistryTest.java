package com.jrl.ai.agent.core;

import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.mock.MockModel;
import com.jrl.ai.agent.core.model.Model;
import com.jrl.ai.agent.core.model.ModelConfig;
import com.jrl.ai.agent.core.model.ModelRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelRegistry 多模型测试 — 验证多模型注册、查找与故障转移。
 */
@DisplayName("ModelRegistry 多模型测试")
class ModelRegistryTest {

    private Map<String, Model> store;
    private ModelRegistry registry;
    private MockModel qwenModel;
    private MockModel gptModel;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        registry = new ModelRegistry() {
            private Model defaultModel;
            @Override public void register(Model m) {
                store.put(m.modelId(), m);
                if (defaultModel == null) defaultModel = m;
            }
            @Override public Optional<Model> resolve(String ref) {
                // 支持 provider:model 格式
                String id = ref.contains(":") ? ref.split(":")[1] : ref;
                return Optional.ofNullable(store.get(id));
            }
            @Override public Optional<Model> defaultModel() { return Optional.ofNullable(defaultModel); }
            @Override public Collection<Model> all() { return List.copyOf(store.values()); }
        };

        qwenModel = new MockModel("qwen-max", "dashscope")
                .withFixedResponse("来自通义千问的回复");
        gptModel = new MockModel("gpt-4.1", "openai")
                .withFixedResponse("Response from GPT-4.1");

        registry.register(qwenModel);
        registry.register(gptModel);
    }

    @Test
    @DisplayName("模型注册：验证模型已注册")
    void testModelRegistration() {
        assertEquals(2, registry.all().size());
        assertTrue(registry.resolve("qwen-max").isPresent());
        assertTrue(registry.resolve("gpt-4.1").isPresent());
    }

    @Test
    @DisplayName("模型解析：支持 provider:model 格式")
    void testModelResolveWithProvider() {
        Optional<Model> model = registry.resolve("dashscope:qwen-max");
        assertTrue(model.isPresent());
        assertEquals("qwen-max", model.get().modelId());
    }

    @Test
    @DisplayName("模型解析：支持直接 modelId")
    void testModelResolveDirect() {
        Optional<Model> model = registry.resolve("gpt-4.1");
        assertTrue(model.isPresent());
        assertEquals("gpt-4.1", model.get().modelId());
    }

    @Test
    @DisplayName("默认模型：返回第一个注册的模型")
    void testDefaultModel() {
        assertTrue(registry.defaultModel().isPresent());
        assertEquals("qwen-max", registry.defaultModel().get().modelId());
    }

    @Test
    @DisplayName("多模型调用：不同模型返回不同响应")
    void testMultiModelCall() {
        List<ChatMessage> messages = List.of(ChatMessage.user("你好"));

        String qwenResponse = qwenModel.call(messages);
        String gptResponse = gptModel.call(messages);

        assertTrue(qwenResponse.contains("通义千问"));
        assertTrue(gptResponse.contains("GPT-4.1"));
        assertNotEquals(qwenResponse, gptResponse);
    }

    @Test
    @DisplayName("故障转移：模型不可用时检测")
    void testFailover() {
        assertTrue(qwenModel.isAvailable());

        qwenModel.withAvailable(false);
        assertFalse(qwenModel.isAvailable());

        // 故障转移到备用模型
        Optional<Model> fallback = registry.resolve("gpt-4.1");
        assertTrue(fallback.isPresent());
        assertTrue(fallback.get().isAvailable());
    }

    @Test
    @DisplayName("ModelConfig：验证模型配置属性")
    void testModelConfig() {
        ModelConfig config = ModelConfig.of("qwen-max", "dashscope");
        assertEquals("qwen-max", config.modelId());
        assertEquals("dashscope", config.provider());
        assertFalse(config.streaming());
        assertFalse(config.toolCalling());
    }

    @Test
    @DisplayName("模型不存在：返回空 Optional")
    void testModelNotFound() {
        Optional<Model> model = registry.resolve("nonexistent-model");
        assertTrue(model.isEmpty());
    }
}
