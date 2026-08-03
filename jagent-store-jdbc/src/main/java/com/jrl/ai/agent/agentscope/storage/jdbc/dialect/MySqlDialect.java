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
 * MySQL 方言 — 使用 ON DUPLICATE KEY UPDATE 实现 upsert。
 */
public class MySqlDialect implements SqlDialect {

    public static final MySqlDialect INSTANCE = new MySqlDialect();

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String kvUpsert() {
        return "INSERT INTO jagent_kv (kv_key, kv_value, updated_at) VALUES (?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE kv_value = VALUES(kv_value), updated_at = NOW()";
    }

    @Override
    public String memoryUpsert() {
        return "INSERT INTO jagent_memory (namespace, mem_key, mem_value, updated_at) VALUES (?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE mem_value = VALUES(mem_value), updated_at = NOW()";
    }

    @Override
    public String stateUpsert() {
        return "INSERT INTO jagent_state (user_id, session_id, state_key, state_value, state_type, updated_at) "
                + "VALUES (?, ?, ?, ?, 'SINGLE', NOW()) "
                + "ON DUPLICATE KEY UPDATE state_value = VALUES(state_value), state_type = 'SINGLE', updated_at = NOW()";
    }

    @Override
    public String stateListUpsert() {
        return "INSERT INTO jagent_state (user_id, session_id, state_key, state_value, state_type, updated_at) "
                + "VALUES (?, ?, ?, ?, 'LIST', NOW()) "
                + "ON DUPLICATE KEY UPDATE state_value = VALUES(state_value), state_type = 'LIST', updated_at = NOW()";
    }
}
