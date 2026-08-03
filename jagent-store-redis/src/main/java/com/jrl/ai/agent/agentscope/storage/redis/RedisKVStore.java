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

import com.jrl.ai.agent.core.storage.KVStore;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 基于 Redis 的 KV 存储实现。
 *
 * <p>Key 格式：{@code jagent:kv:{key}}
 */
public class RedisKVStore implements KVStore {

    private static final Logger log = LoggerFactory.getLogger(RedisKVStore.class);
    private static final String KEY_PREFIX = "jagent:kv:";

    private final RedisCommands<String, String> commands;

    public RedisKVStore(RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public void put(String key, String value) {
        commands.set(redisKey(key), value);
    }

    @Override
    public Optional<String> get(String key) {
        String value = commands.get(redisKey(key));
        return Optional.ofNullable(value);
    }

    @Override
    public void delete(String key) {
        commands.del(redisKey(key));
    }

    @Override
    public boolean exists(String key) {
        Long result = commands.exists(redisKey(key));
        return result != null && result > 0;
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
