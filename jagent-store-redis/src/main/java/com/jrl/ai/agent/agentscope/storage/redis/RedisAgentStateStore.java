/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.storage.redis;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的 Agent 状态存储实现。
 *
 * <p>Redis Key 设计：
 * <ul>
 *   <li>Single state: {@code jagent:state:{userId}:{sessionId}:{key}} → JSON string</li>
 *   <li>List state: {@code jagent:state:{userId}:{sessionId}:{key}:list} → Redis List</li>
 *   <li>Session 索引: {@code jagent:sessions:{userId}} → Redis Set of sessionIds</li>
 * </ul>
 */
public class RedisAgentStateStore implements AgentStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAgentStateStore.class);
    private static final String STATE_PREFIX = "jagent:state:";
    private static final String SESSIONS_PREFIX = "jagent:sessions:";
    private static final String ANON_USER = "__anon__";

    private final RedisCommands<String, String> commands;
    private final JsonCodec jsonCodec;

    public RedisAgentStateStore(RedisCommands<String, String> commands) {
        this.commands = commands;
        this.jsonCodec = new JsonCodec();
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        String redisKey = stateKey(userId, sessionId, key);
        String json = jsonCodec.toJson(value);
        commands.set(redisKey, json);
        indexSession(userId, sessionId);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        String redisKey = listKey(userId, sessionId, key);
        // 先删除旧列表，再写入新列表
        commands.del(redisKey);
        if (values != null && !values.isEmpty()) {
            String[] jsons = values.stream()
                    .map(jsonCodec::toJson)
                    .toArray(String[]::new);
            commands.rpush(redisKey, jsons);
        }
        indexSession(userId, sessionId);
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        String redisKey = stateKey(userId, sessionId, key);
        String json = commands.get(redisKey);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(jsonCodec.fromJson(json, type));
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        String redisKey = listKey(userId, sessionId, key);
        List<String> jsons = commands.lrange(redisKey, 0, -1);
        if (jsons == null || jsons.isEmpty()) {
            return List.of();
        }
        return jsons.stream()
                .map(json -> jsonCodec.fromJson(json, itemType))
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        String sessionsKey = sessionsKey(userId);
        Boolean result = commands.sismember(sessionsKey, sessionId);
        return result != null && result;
    }

    @Override
    public void delete(String userId, String sessionId) {
        // 删除该 session 下的所有 state keys
        String pattern = STATE_PREFIX + normalizeUser(userId) + ":" + sessionId + ":*";
        // 使用 scan 避免阻塞
        var keys = commands.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            commands.del(keys.toArray(new String[0]));
        }
        // 从 session 索引中移除
        commands.srem(sessionsKey(userId), sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        commands.del(stateKey(userId, sessionId, key));
        commands.del(listKey(userId, sessionId, key));
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        Set<String> members = commands.smembers(sessionsKey(userId));
        return members != null ? members : Set.of();
    }

    @Override
    public void close() {
        // Lettuce 连接由外部管理，此处不关闭
    }

    // ==================== Key 构建 ====================

    private String stateKey(String userId, String sessionId, String key) {
        return STATE_PREFIX + normalizeUser(userId) + ":" + sessionId + ":" + key;
    }

    private String listKey(String userId, String sessionId, String key) {
        return stateKey(userId, sessionId, key) + ":list";
    }

    private String sessionsKey(String userId) {
        return SESSIONS_PREFIX + normalizeUser(userId);
    }

    private void indexSession(String userId, String sessionId) {
        commands.sadd(sessionsKey(userId), sessionId);
    }

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? ANON_USER : userId;
    }
}
