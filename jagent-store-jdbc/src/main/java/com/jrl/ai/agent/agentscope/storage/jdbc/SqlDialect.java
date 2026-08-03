/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.storage.jdbc;

/**
 * SQL 方言抽象 — 屏蔽不同数据库的 upsert 语法差异。
 *
 * <p>实现类需保证线程安全（无状态，可共享单例）。
 */
public interface SqlDialect {

    /**
     * 方言名称，用于日志和配置校验。
     */
    String name();

    /**
     * 生成 KV 存储的 upsert SQL。
     *
     * @return 带占位符 (?) 的 INSERT ... ON CONFLICT/DUPLICATE KEY 语句
     */
    String kvUpsert();

    /**
     * 生成记忆存储的 upsert SQL。
     *
     * @return 带占位符 (?) 的 INSERT ... ON CONFLICT/DUPLICATE KEY 语句
     */
    String memoryUpsert();

    /**
     * 生成 Agent 状态存储的 upsert SQL（单值，state_type = 'SINGLE'）。
     *
     * @return 带占位符 (?) 的 INSERT ... ON CONFLICT/DUPLICATE KEY 语句
     */
    String stateUpsert();

    /**
     * 生成 Agent 状态存储的 upsert SQL（列表，state_type = 'LIST'）。
     *
     * @return 带占位符 (?) 的 INSERT ... ON CONFLICT/DUPLICATE KEY 语句
     */
    String stateListUpsert();
}
