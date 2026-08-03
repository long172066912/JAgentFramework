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

import com.jrl.ai.agent.agentscope.storage.DistributedStoreProvider;
import com.jrl.ai.agent.core.memory.MemoryStore;
import com.jrl.ai.agent.core.storage.KVStore;
import io.agentscope.core.state.AgentStateStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 分布式存储提供器 — 通过 SPI 自动发现。
 *
 * <p>支持的配置项：
 * <ul>
 *   <li>host — Redis 主机（默认 localhost）</li>
 *   <li>port — Redis 端口（默认 6379）</li>
 *   <li>password — 密码（可选）</li>
 *   <li>database — 数据库编号（默认 0）</li>
 * </ul>
 */
public class RedisStoreProvider implements DistributedStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisStoreProvider.class);

    private volatile RedisClient client;
    private volatile StatefulRedisConnection<String, String> connection;

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public KVStore createKVStore(Map<String, String> config) {
        return new RedisKVStore(commands(config));
    }

    @Override
    public MemoryStore createMemoryStore(Map<String, String> config) {
        return new RedisMemoryStore(commands(config));
    }

    @Override
    public AgentStateStore createAgentStateStore(Map<String, String> config) {
        return new RedisAgentStateStore(commands(config));
    }

    /**
     * 获取或创建 Redis 连接（懒初始化，线程安全）。
     */
    private RedisCommands<String, String> commands(Map<String, String> config) {
        if (connection == null) {
            synchronized (this) {
                if (connection == null) {
                    RedisURI uri = buildUri(config);
                    client = RedisClient.create(uri);
                    connection = client.connect();
                    log.info("Redis connection established: {}:{}", uri.getHost(), uri.getPort());
                }
            }
        }
        return connection.sync();
    }

    private RedisURI buildUri(Map<String, String> config) {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(config.getOrDefault("host", "localhost"))
                .withPort(Integer.parseInt(config.getOrDefault("port", "6379")))
                .withDatabase(Integer.parseInt(config.getOrDefault("database", "0")))
                .withTimeout(Duration.ofSeconds(10));

        String password = config.get("password");
        if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }

        return builder.build();
    }
}
