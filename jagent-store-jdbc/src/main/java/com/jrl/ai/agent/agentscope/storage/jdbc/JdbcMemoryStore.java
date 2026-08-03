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

import com.jrl.ai.agent.core.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的记忆存储实现，通过 {@link SqlDialect} 适配不同数据库。
 *
 * <p>表：{@code jagent_memory}（namespace + mem_key 联合主键）
 */
public class JdbcMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryStore.class);

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcMemoryStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public void put(String namespace, String key, String value) {
        String sql = dialect.memoryUpsert();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to put memory: " + namespace + "/" + key, e);
        }
    }

    @Override
    public Optional<String> get(String namespace, String key) {
        String sql = "SELECT mem_value FROM jagent_memory WHERE namespace = ? AND mem_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("mem_value"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get memory: " + namespace + "/" + key, e);
        }
    }

    @Override
    public List<String> keys(String namespace) {
        String sql = "SELECT mem_key FROM jagent_memory WHERE namespace = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> keys = new ArrayList<>();
                while (rs.next()) {
                    keys.add(rs.getString("mem_key"));
                }
                return keys;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list keys for namespace: " + namespace, e);
        }
    }

    @Override
    public void remove(String namespace, String key) {
        String sql = "DELETE FROM jagent_memory WHERE namespace = ? AND mem_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove memory: " + namespace + "/" + key, e);
        }
    }

    @Override
    public void clear(String namespace) {
        String sql = "DELETE FROM jagent_memory WHERE namespace = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear namespace: " + namespace, e);
        }
    }
}
