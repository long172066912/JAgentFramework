package com.jrl.ai.agent.agentscope.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容模型 — 支持所有兼容 OpenAI Chat Completions API 的代理服务。
 *
 * <p>适用场景：TokenPay、OneAPI 等 API 聚合代理，
 * 这些服务使用 {@code /v1/chat/completions} 端点和 Bearer Token 认证。
 *
 * <p>支持流式（SSE）和非流式两种模式。
 */
public class OpenAICompatibleModel extends ChatModelBase {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleModel.class);

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final boolean stream;
    private final HttpClient httpClient;

    /**
     * 创建 OpenAI 兼容模型。
     *
     * @param apiKey    API Key（Bearer Token）
     * @param modelName 模型名称（如 "qwen-plus"、"gpt-4o"）
     * @param baseUrl   API Base URL（如 "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"）
     * @param stream    是否启用流式
     */
    public OpenAICompatibleModel(String apiKey, String modelName, String baseUrl, boolean stream) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.stream = stream;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        log.info("OpenAICompatibleModel created: model={} baseUrl={}", modelName, this.baseUrl);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        if (stream) {
            return doStreamSSE(messages);
        } else {
            return doNonStream(messages);
        }
    }

    /**
     * 非流式调用 — 一次性返回完整结果。
     */
    private Flux<ChatResponse> doNonStream(List<Msg> messages) {
        try {
            String requestBody = buildRequestBody(messages, false);
            HttpRequest request = buildHttpRequest(requestBody);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[OpenAI] HTTP {} : {}", response.statusCode(), response.body());
                return Flux.error(new RuntimeException("HTTP " + response.statusCode() + ": " + response.body()));
            }

            ChatResponse chatResponse = parseNonStreamResponse(response.body());
            return Flux.just(chatResponse);
        } catch (Exception e) {
            log.error("[OpenAI] Non-stream request failed", e);
            return Flux.error(e);
        }
    }

    /**
     * 流式调用 — SSE 逐块返回。
     */
    private Flux<ChatResponse> doStreamSSE(List<Msg> messages) {
        try {
            String requestBody = buildRequestBody(messages, true);
            HttpRequest request = buildHttpRequest(requestBody);

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                log.error("[OpenAI] HTTP {} : {}", response.statusCode(), errorBody);
                return Flux.error(new RuntimeException("HTTP " + response.statusCode() + ": " + errorBody));
            }

            return Flux.create(sink -> {
                response.body().forEach(line -> {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            sink.complete();
                            return;
                        }
                        try {
                            ChatResponse chunk = parseStreamChunk(data);
                            if (chunk != null) {
                                sink.next(chunk);
                            }
                        } catch (Exception e) {
                            log.warn("[OpenAI] Failed to parse SSE chunk: {}", data, e);
                        }
                    }
                });
                sink.complete();
            });
        } catch (Exception e) {
            log.error("[OpenAI] Stream request failed", e);
            return Flux.error(e);
        }
    }

    /**
     * 构建 OpenAI Chat Completions 请求体。
     */
    private String buildRequestBody(List<Msg> messages, boolean useStream) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(modelName)).append("\"");
        sb.append(",\"stream\":").append(useStream);
        sb.append(",\"messages\":[");

        for (int i = 0; i < messages.size(); i++) {
            Msg msg = messages.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"role\":\"").append(toOpenAIRole(msg.getRole())).append("\"");
            sb.append(",\"content\":").append(jsonString(msg.getTextContent()));
            sb.append("}");
        }

        sb.append("]}");
        return sb.toString();
    }

    /**
     * 构建 HTTP 请求。
     */
    private HttpRequest buildHttpRequest(String body) {
        String url = baseUrl + "/chat/completions";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    /**
     * 解析非流式响应。
     */
    private ChatResponse parseNonStreamResponse(String json) {
        String content = extractJsonString(json, "content");
        if (content == null) {
            // 尝试从 choices[0].message.content 提取
            content = extractNestedString(json, "choices", "message", "content");
        }

        int promptTokens = extractJsonInt(json, "prompt_tokens");
        int completionTokens = extractJsonInt(json, "completion_tokens");

        ChatUsage usage = ChatUsage.builder()
                .inputTokens(promptTokens)
                .outputTokens(completionTokens)
                .build();

        List<ContentBlock> contentBlocks = List.of(TextBlock.builder().text(content != null ? content : "").build());

        return ChatResponse.builder()
                .content(contentBlocks)
                .usage(usage)
                .finishReason("stop")
                .build();
    }

    /**
     * 解析流式 chunk。
     */
    private ChatResponse parseStreamChunk(String json) {
        String delta = extractNestedString(json, "choices", "delta", "content");
        if (delta == null || delta.isEmpty()) {
            // 可能是空 chunk（role 定义等）
            String role = extractNestedString(json, "choices", "delta", "role");
            if (role != null) return null; // 跳过 role-only chunk
            delta = "";
        }

        List<ContentBlock> contentBlocks = List.of(TextBlock.builder().text(delta).build());
        return ChatResponse.builder()
                .content(contentBlocks)
                .finishReason("stop")
                .build();
    }

    // ========== JSON 工具方法（轻量实现，避免引入额外依赖） ==========

    private String toOpenAIRole(MsgRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + escapeJson(value) + "\"";
    }

    /**
     * 从 JSON 中提取顶层字符串字段。
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int start = json.indexOf('"', colonIdx + 1);
        if (start < 0) return null;
        int end = findStringEnd(json, start + 1);
        return json.substring(start + 1, end);
    }

    /**
     * 从 JSON 中提取嵌套字段（如 choices[0].message.content）。
     */
    private String extractNestedString(String json, String... keys) {
        String current = json;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            // 处理数组索引（简单支持 [0]）
            int arrIdx = current.indexOf("[");
            int keyIdx = current.indexOf("\"" + key + "\"");

            if (arrIdx >= 0 && (keyIdx < 0 || arrIdx < keyIdx)) {
                // 进入数组第一个元素
                int objStart = current.indexOf('{', arrIdx);
                if (objStart < 0) return null;
                current = current.substring(objStart);
                continue;
            }

            if (keyIdx < 0) return null;
            int colonIdx = current.indexOf(':', keyIdx + key.length() + 2);
            if (colonIdx < 0) return null;

            if (i == keys.length - 1) {
                // 最后一个 key，提取字符串值
                int start = current.indexOf('"', colonIdx + 1);
                if (start < 0) return null;
                int end = findStringEnd(current, start + 1);
                return current.substring(start + 1, end);
            } else {
                // 中间 key，继续深入
                int objStart = current.indexOf('{', colonIdx + 1);
                if (objStart < 0) return null;
                current = current.substring(objStart);
            }
        }
        return null;
    }

    private int extractJsonInt(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return 0;
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (start == end) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int findStringEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++; // 跳过转义字符
            } else if (c == '"') {
                return i;
            }
        }
        return json.length();
    }
}
