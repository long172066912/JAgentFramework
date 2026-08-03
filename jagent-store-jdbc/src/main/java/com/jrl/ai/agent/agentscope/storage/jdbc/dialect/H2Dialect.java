/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.storage.jdbc.dialect;

import com.jrl.ai.agent.agentscope.storage.jdbc.SqlDialect;

/**
 * H2 方言 — 使用标准 SQL MERGE INTO 实现 upsert（适用于开发/测试环境）。
 */
public class H2Dialect implements SqlDialect {

    public static final H2Dialect INSTANCE = new H2Dialect();

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public String kvUpsert() {
        return "MERGE INTO jagent_kv (kv_key, kv_value, updated_at) KEY (kv_key) VALUES (?, ?, CURRENT_TIMESTAMP)";
    }

    @Override
    public String memoryUpsert() {
        return "MERGE INTO jagent_memory (namespace, mem_key, mem_value, updated_at) "
                + "KEY (namespace, mem_key) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
    }

    @Override
    public String stateUpsert() {
        return "MERGE INTO jagent_state (user_id, session_id, state_key, state_value, state_type, updated_at) "
                + "KEY (user_id, session_id, state_key) VALUES (?, ?, ?, ?, 'SINGLE', CURRENT_TIMESTAMP)";
    }

    @Override
    public String stateListUpsert() {
        return "MERGE INTO jagent_state (user_id, session_id, state_key, state_value, state_type, updated_at) "
                + "KEY (user_id, session_id, state_key) VALUES (?, ?, ?, ?, 'LIST', CURRENT_TIMESTAMP)";
    }
}
