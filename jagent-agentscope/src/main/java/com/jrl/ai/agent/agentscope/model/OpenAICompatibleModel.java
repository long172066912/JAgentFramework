package com.jrl.ai.agent.agentscope.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
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
    private final boolean enableThinking;
    private final HttpClient httpClient;

    /**
     * 创建 OpenAI 兼容模型。
     *
     * @param apiKey         API Key（Bearer Token）
     * @param modelName      模型名称（如 "qwen-plus"、"gpt-4o"）
     * @param baseUrl        API Base URL（如 "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"）
     * @param stream         是否启用流式
     * @param enableThinking 是否启用推理/思考模式（默认 false，开启后响应更慢但更深入）
     */
    public OpenAICompatibleModel(String apiKey, String modelName, String baseUrl, boolean stream, boolean enableThinking) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.stream = stream;
        this.enableThinking = enableThinking;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        log.info("OpenAICompatibleModel created: model={} baseUrl={} thinking={}", modelName, this.baseUrl, enableThinking);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        if (stream) {
            return doStreamSSE(messages, tools);
        } else {
            return doNonStream(messages, tools);
        }
    }

    /**
     * 非流式调用 — 一次性返回完整结果。
     */
    private Flux<ChatResponse> doNonStream(List<Msg> messages, List<ToolSchema> tools) {
        try {
            String requestBody = buildRequestBody(messages, false, tools);
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
    private Flux<ChatResponse> doStreamSSE(List<Msg> messages, List<ToolSchema> tools) {
        try {
            String requestBody = buildRequestBody(messages, true, tools);
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
    private String buildRequestBody(List<Msg> messages, boolean useStream, List<ToolSchema> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(modelName)).append("\"");
        sb.append(",\"stream\":").append(useStream);
        // 推理/思考模式（默认不传此参数，配置开启时才传递）
        if (enableThinking) {
            sb.append(",\"enable_thinking\":true");
        }
        sb.append(",\"messages\":[");

        for (int i = 0; i < messages.size(); i++) {
            Msg msg = messages.get(i);
            if (i > 0) sb.append(",");
            sb.append(serializeMsg(msg));
        }

        sb.append("]");

        // 添加工具列表
        if (tools != null && !tools.isEmpty()) {
            sb.append(",\"tools\":[");
            for (int i = 0; i < tools.size(); i++) {
                if (i > 0) sb.append(",");
                ToolSchema tool = tools.get(i);
                sb.append("{\"type\":\"function\"");
                sb.append(",\"function\":{");
                sb.append("\"name\":\"").append(escapeJson(tool.getName())).append("\"");
                sb.append(",\"description\":\"").append(escapeJson(tool.getDescription())).append("\"");
                Map<String, Object> params = tool.getParameters();
                if (params != null && !params.isEmpty()) {
                    sb.append(",\"parameters\":").append(toJsonString(params));
                } else {
                    sb.append(",\"parameters\":{\"type\":\"object\",\"properties\":{}}");
                }
                sb.append("}}");
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 将 Map 转为 JSON 字符串。
     */
    private String toJsonString(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) return jsonString(s);
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(jsonString(entry.getKey().toString()));
                sb.append(":");
                sb.append(toJsonString(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJsonString(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return jsonString(obj.toString());
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
     * 解析非流式响应 — 使用 Jackson 完整解析 JSON。
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseNonStreamResponse(String json) {
        Map<String, Object> root;
        try {
            root = OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.error("[OpenAI] Failed to parse response JSON", e);
            return ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text("").build()))
                    .finishReason("stop")
                    .build();
        }

        // 提取 usage
        Map<String, Object> usage = (Map<String, Object>) root.get("usage");
        int promptTokens = usage != null ? ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue() : 0;
        int completionTokens = usage != null ? ((Number) usage.getOrDefault("completion_tokens", 0)).intValue() : 0;
        ChatUsage chatUsage = ChatUsage.builder()
                .inputTokens(promptTokens)
                .outputTokens(completionTokens)
                .build();

        // 提取 choices[0].message
        List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
        if (choices == null || choices.isEmpty()) {
            return ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text("").build()))
                    .usage(chatUsage)
                    .finishReason("stop")
                    .build();
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text("").build()))
                    .usage(chatUsage)
                    .finishReason("stop")
                    .build();
        }

        List<ContentBlock> contentBlocks = new ArrayList<>();

        // 提取 content（避免 reasoning_content）
        String content = (String) message.get("content");
        if (content != null && !content.isEmpty()) {
            contentBlocks.add(TextBlock.builder().text(content).build());
        }

        // 提取 tool_calls
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null) {
            for (Map<String, Object> tc : toolCalls) {
                ToolUseBlock block = buildToolUseBlockFromMap(tc);
                if (block != null) contentBlocks.add(block);
            }
        }

        if (contentBlocks.isEmpty()) {
            contentBlocks.add(TextBlock.builder().text("").build());
        }

        // 提取 finish_reason
        String finishReason = (String) choices.get(0).get("finish_reason");
        if (finishReason == null || finishReason.isEmpty()) {
            finishReason = toolCalls != null && !toolCalls.isEmpty() ? "tool_calls" : "stop";
        }

        return ChatResponse.builder()
                .content(contentBlocks)
                .usage(chatUsage)
                .finishReason(finishReason)
                .build();
    }

    /**
     * 解析流式 chunk — 使用 Jackson 解析。
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseStreamChunk(String json) {
        Map<String, Object> root;
        try {
            root = OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("[OpenAI] Failed to parse stream chunk: {}", json, e);
            return null;
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
        if (choices == null || choices.isEmpty()) return null;

        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
        if (delta == null) return null;

        String contentText = (String) delta.get("content");
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");

        if ((contentText == null || contentText.isEmpty()) && (toolCalls == null || toolCalls.isEmpty())) {
            // 可能是 role-only chunk
            String role = (String) delta.get("role");
            if (role != null) return null;
            return null;
        }

        List<ContentBlock> contentBlocks = new ArrayList<>();
        if (contentText != null && !contentText.isEmpty()) {
            contentBlocks.add(TextBlock.builder().text(contentText).build());
        }

        if (toolCalls != null) {
            for (Map<String, Object> tc : toolCalls) {
                ToolUseBlock block = buildToolUseBlockFromMap(tc);
                if (block != null) contentBlocks.add(block);
            }
        }

        return ChatResponse.builder()
                .content(contentBlocks)
                .finishReason("stop")
                .build();
    }

    /**
     * 从已解析的 tool_call Map 构建 ToolUseBlock。
     */
    @SuppressWarnings("unchecked")
    private ToolUseBlock buildToolUseBlockFromMap(Map<String, Object> toolCall) {
        if (toolCall == null) return null;
        String id = (String) toolCall.get("id");
        if (id == null || id.isEmpty()) id = "call_" + System.nanoTime();

        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
        if (function == null) return null;

        String name = (String) function.get("name");
        if (name == null || name.isEmpty()) return null;

        String argsStr = (String) function.get("arguments");
        Map<String, Object> input = parseJsonArgs(argsStr != null ? argsStr : "{}");

        return ToolUseBlock.builder()
                .id(id)
                .name(name)
                .input(input)
                .build();
    }

    /**
     * 解析 JSON 参数字符串为 Map。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private Map<String, Object> parseJsonArgs(String args) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (args == null || args.isBlank()) return result;
        try {
            return OBJECT_MAPPER.readValue(args, MAP_TYPE);
        } catch (Exception e) {
            log.warn("[OpenAI] Failed to parse arguments JSON: {}", args, e);
            return result;
        }
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

    /**
     * 将 AgentScope {@link Msg} 序列化为 OpenAI Chat Completions 格式的 JSON 对象。
     *
     * <p>需要正确处理三种特殊情况（这是该方法的实现重点）：
     * <ul>
     *   <li>ASSISTANT 消息包含 {@link ToolUseBlock} — 需输出 {@code tool_calls} 数组，
     *       并将 {@code content} 设为 null</li>
     *   <li>TOOL 消息包含 {@link ToolResultBlock} — 需输出 {@code tool_call_id}，
     *       并将 {@code content} 设为工具结果文本</li>
     *   <li>普通消息 — 仅输出 {@code role} 和 {@code content}</li>
     * </ul>
     *
     * <p>如果丢失 tool_calls / tool_call_id 字段，OpenAI 会报错：
     * {@code "messages with role 'tool' must be a response to a preceeding tool_calls"}.
     */
    private String serializeMsg(Msg msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"role\":\"").append(toOpenAIRole(msg.getRole())).append("\"");

        MsgRole role = msg.getRole();
        List<ContentBlock> blocks = msg.getContent();

        if (role == MsgRole.TOOL) {
            // 工具响应消息：必须包含 tool_call_id
            String toolCallId = null;
            String textContent = "";
            if (blocks != null) {
                for (ContentBlock block : blocks) {
                    if (block instanceof ToolResultBlock trb) {
                        if (toolCallId == null && trb.getId() != null) {
                            toolCallId = trb.getId();
                        }
                        if (trb.getOutput() != null) {
                            for (ContentBlock out : trb.getOutput()) {
                                if (out instanceof TextBlock tb && tb.getText() != null) {
                                    textContent = tb.getText();
                                    break;
                                }
                            }
                        }
                        if (textContent.isEmpty() && trb.getOutput() == null) {
                            // 错误结果的兼容处理
                            textContent = trb.getName() != null ? trb.getName() : "";
                        }
                    }
                }
            }
            if (toolCallId == null) toolCallId = "";
            sb.append(",\"tool_call_id\":\"").append(escapeJson(toolCallId)).append("\"");
            sb.append(",\"content\":").append(jsonString(textContent));
        } else if (role == MsgRole.ASSISTANT) {
            // Assistant 消息：检查是否有 tool_calls
            String textContent = msg.getTextContent();
            List<ToolUseBlock> toolUses = (blocks != null)
                    ? msg.getContentBlocks(ToolUseBlock.class)
                    : java.util.Collections.emptyList();

            if (!toolUses.isEmpty()) {
                // 有 tool_calls：content 设为 null，输出 tool_calls 数组
                if (textContent == null) textContent = "";
                if (!textContent.isEmpty()) {
                    sb.append(",\"content\":").append(jsonString(textContent));
                } else {
                    sb.append(",\"content\":null");
                }
                sb.append(",\"tool_calls\":[");
                for (int i = 0; i < toolUses.size(); i++) {
                    if (i > 0) sb.append(",");
                    ToolUseBlock tu = toolUses.get(i);
                    sb.append("{\"id\":\"").append(escapeJson(tu.getId() != null ? tu.getId() : "")).append("\"");
                    sb.append(",\"type\":\"function\"");
                    sb.append(",\"function\":{");
                    sb.append("\"name\":\"").append(escapeJson(tu.getName() != null ? tu.getName() : "")).append("\"");
                    String argsStr = "{}";
                    Map<String, Object> input = tu.getInput();
                    if (input != null && !input.isEmpty()) {
                        try {
                            argsStr = OBJECT_MAPPER.writeValueAsString(input);
                        } catch (Exception e) {
                            log.warn("[OpenAI] Failed to serialize tool input for {}", tu.getName(), e);
                        }
                    }
                    sb.append(",\"arguments\":").append(jsonString(argsStr));
                    sb.append("}}");
                }
                sb.append("]");
            } else {
                // 纯文本 assistant 消息
                if (textContent == null) textContent = "";
                sb.append(",\"content\":").append(jsonString(textContent));
            }
        } else {
            // SYSTEM / USER 消息：仅 content
            String textContent = msg.getTextContent();
            if (textContent == null) textContent = "";
            sb.append(",\"content\":").append(jsonString(textContent));
        }

        sb.append("}");
        return sb.toString();
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
     * 从 JSON 中提取顶层字符串字段（精确 key 匹配，避免子串误匹配）。
     * 例如：搜索 "content" 不会误匹配 "reasoning_content"。
     */
    private String extractExactJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = 0;
        while (idx < json.length()) {
            int found = json.indexOf(pattern, idx);
            if (found < 0) return null;
            // 检查 found 前一个字符是否为 { 或 , （确保是独立 key，不是更长 key 的后缀）
            if (found > 0) {
                char before = json.charAt(found - 1);
                if (before == '{' || before == ',') {
                    int colonIdx = json.indexOf(':', found + pattern.length());
                    if (colonIdx < 0) return null;
                    int start = json.indexOf('"', colonIdx + 1);
                    if (start < 0) return null;
                    int end = findStringEnd(json, start + 1);
                    return json.substring(start + 1, end);
                }
            }
            idx = found + 1;
        }
        return null;
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

    /**
     * 从 JSON 中精确提取嵌套字段（避免子串 key 误匹配）。
     * 专用于 choices[0].delta/message 下的字段提取。
     */
    private String extractExactNestedString(String json, String targetKey) {
        // 先找到 choices 数组
        int choicesIdx = json.indexOf("\"choices\"");
        if (choicesIdx < 0) return null;
        int arrStart = json.indexOf('[', choicesIdx);
        if (arrStart < 0) return null;
        int objStart = json.indexOf('{', arrStart);
        if (objStart < 0) return null;

        // 在 choices[0] 内找 message 或 delta
        String sub = json.substring(objStart);
        int msgIdx = sub.indexOf("\"message\"");
        int deltaIdx = sub.indexOf("\"delta\"");
        int containerIdx = msgIdx >= 0 ? msgIdx : deltaIdx;
        if (containerIdx < 0) return null;

        int braceStart = sub.indexOf('{', containerIdx);
        if (braceStart < 0) return null;

        // 在 message/delta 对象内精确匹配 targetKey
        String container = sub.substring(braceStart);
        String pattern = "\"" + targetKey + "\"";
        int idx = 0;
        while (idx < container.length()) {
            int found = container.indexOf(pattern, idx);
            if (found < 0) return null;
            if (found > 0) {
                char before = container.charAt(found - 1);
                if (before == '{' || before == ',') {
                    int colonIdx = container.indexOf(':', found + pattern.length());
                    if (colonIdx < 0) return null;
                    int start = container.indexOf('"', colonIdx + 1);
                    if (start < 0) return null;
                    int end = findStringEnd(container, start + 1);
                    return container.substring(start + 1, end);
                }
            }
            idx = found + 1;
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
