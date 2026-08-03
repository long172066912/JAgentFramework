/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.storage;

import com.jrl.ai.agent.core.memory.MemoryStore;
import com.jrl.ai.agent.core.storage.KVStore;
import io.agentscope.core.state.AgentStateStore;

import java.util.Map;

/**
 * 分布式存储提供器 SPI — 通过 Java ServiceLoader 自动发现。
 *
 * <p>每个实现对应一种分布式存储后端（如 Redis、MySQL），
 * 负责创建 KVStore、MemoryStore、AgentStateStore 的分布式实现。
 *
 * <p>使用方只需在 classpath 中引入对应的 store 模块 jar，
 * 框架会自动通过 SPI 发现并加载。
 */
public interface DistributedStoreProvider {

    /**
     * 提供器标识（如 "redis"、"mysql"），用于 YAML 配置匹配。
     *
     * @return 提供器名称
     */
    String name();

    /**
     * 创建 KV 存储实例。
     *
     * @param config 后端连接配置（从 YAML 展平后的 key-value）
     * @return KVStore 实现
     */
    KVStore createKVStore(Map<String, String> config);

    /**
     * 创建记忆存储实例。
     *
     * @param config 后端连接配置
     * @return MemoryStore 实现
     */
    MemoryStore createMemoryStore(Map<String, String> config);

    /**
     * 创建 Agent 状态存储实例。
     *
     * @param config 后端连接配置
     * @return AgentStateStore 实现
     */
    AgentStateStore createAgentStateStore(Map<String, String> config);
}
