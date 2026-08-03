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

import com.jrl.ai.agent.core.memory.MemoryStore;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Redis Hash 的记忆存储实现。
 *
 * <p>使用 Redis Hash 结构，namespace 作为 hash key，记忆条目作为 field。
 * <p>Key 格式：{@code jagent:mem:{namespace}}
 */
public class RedisMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisMemoryStore.class);
    private static final String KEY_PREFIX = "jagent:mem:";

    private final RedisCommands<String, String> commands;

    public RedisMemoryStore(RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public void put(String namespace, String key, String value) {
        commands.hset(redisKey(namespace), key, value);
    }

    @Override
    public Optional<String> get(String namespace, String key) {
        String value = commands.hget(redisKey(namespace), key);
        return Optional.ofNullable(value);
    }

    @Override
    public List<String> keys(String namespace) {
        Map<String, String> entries = commands.hgetall(redisKey(namespace));
        return entries != null ? new ArrayList<>(entries.keySet()) : List.of();
    }

    @Override
    public void remove(String namespace, String key) {
        commands.hdel(redisKey(namespace), key);
    }

    @Override
    public void clear(String namespace) {
        commands.del(redisKey(namespace));
    }

    private String redisKey(String namespace) {
        return KEY_PREFIX + namespace;
    }
}
