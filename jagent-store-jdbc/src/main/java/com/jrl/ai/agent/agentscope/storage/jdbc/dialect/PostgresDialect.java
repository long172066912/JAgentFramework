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
 * PostgreSQL 方言 — 使用 ON CONFLICT ... DO UPDATE SET 实现 upsert。
 */
public class PostgresDialect implements SqlDialect {

    public static final PostgresDialect INSTANCE = new PostgresDialect();

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public String kvUpsert() {
        return "INSERT INTO jagent_kv (kv_key, kv_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (kv_key) DO UPDATE SET kv_value = EXCLUDED.kv_value, updated_at = CURRENT_TIMESTAMP";
    }

    @Override
    public String memoryUpsert() {
        return "INSERT INTO jagent_memory (namespace, mem_key, mem_value, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (namespace, mem_key) DO UPDATE SET mem_value = EXCLUDED.mem_value, updated_at = CURRENT_TIMESTAMP";
    }

    @Override
    public String stateUpsert() {
        return "INSERT INTO jagent_state (user_id, session_id, state_key, state_value, state_type, updated_at) "
                + "VALUES (?, ?, ?, ?, 'SINGLE', CURRENT_TIMESTAMP) "
                + "ON CONFLICT (user_id, session_id, state_key) DO UPDATE SET "
                + "state_value = EXCLUDED.state_value, state_type = 'SINGLE', updated_at = CURRENT_TIMESTAMP";
    }

    @Override
    public String stateListUpsert() {
        return "INSERT INTO jagent_state (user_id, session_id, state_key, state_value, state_type, updated_at) "
                + "VALUES (?, ?, ?, ?, 'LIST', CURRENT_TIMESTAMP) "
                + "ON CONFLICT (user_id, session_id, state_key) DO UPDATE SET "
                + "state_value = EXCLUDED.state_value, state_type = 'LIST', updated_at = CURRENT_TIMESTAMP";
    }
}
