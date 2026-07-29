package com.jrl.ai.agent.demo.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具类 — 封装 Jackson 常用操作。
 *
 * <p>提供静态方法，内部共享单例 ObjectMapper，避免重复创建。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private JsonUtil() {
        // 工具类禁止实例化
    }

    /**
     * 获取内部 ObjectMapper 实例（高级用法）。
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 解析 JSON 字符串为 JsonNode。
     *
     * @param json JSON 字符串
     * @return JsonNode，解析失败返回 null
     */
    public static JsonNode parseTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析 JSON 字符串为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 解析结果，失败返回 null
     */
    public static <T> T parse(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析 JSON 字符串为复杂泛型类型（如 List、Map）。
     *
     * @param json          JSON 字符串
     * @param typeReference 类型引用，如 {@code new TypeReference<List<Tag>>() {}}
     * @param <T>           目标类型泛型
     * @return 解析结果，失败返回 null
     */
    public static <T> T parse(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析 JSON 字符串为 List。
     */
    public static <T> List<T> parseList(String json, Class<T> elementClass) {
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析 JSON 字符串为 Map。
     */
    public static Map<String, Object> parseMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 对象序列化为 JSON 字符串。
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，失败返回 null
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 对象序列化为格式化 JSON 字符串。
     */
    public static String toPrettyJson(Object obj) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 去除字符串中的 markdown 代码块标记，提取纯 JSON。
     *
     * <p>LLM 输出常包含 {@code ```json ... ```} 包裹，此方法可安全提取内容。
     *
     * @param text 可能包含 markdown 标记的文本
     * @return 纯 JSON 字符串
     */
    public static String extractJson(String text) {
        if (text == null) return null;
        return text.replaceAll("(?s)```json\\s*", "")
                   .replaceAll("(?s)```\\s*$", "")
                   .trim();
    }
}
