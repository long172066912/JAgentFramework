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

import com.jrl.ai.agent.core.storage.KVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

/**
 * 基于 JDBC 的 KV 存储实现，通过 {@link SqlDialect} 适配不同数据库。
 *
 * <p>表：{@code jagent_kv}（kv_key VARCHAR PRIMARY KEY, kv_value TEXT）
 */
public class JdbcKVStore implements KVStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcKVStore.class);

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcKVStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public void put(String key, String value) {
        String sql = dialect.kvUpsert();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to put KV: " + key, e);
        }
    }

    @Override
    public Optional<String> get(String key) {
        String sql = "SELECT kv_value FROM jagent_kv WHERE kv_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("kv_value"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get KV: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        String sql = "DELETE FROM jagent_kv WHERE kv_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete KV: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        String sql = "SELECT 1 FROM jagent_kv WHERE kv_key = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check KV exists: " + key, e);
        }
    }
}
