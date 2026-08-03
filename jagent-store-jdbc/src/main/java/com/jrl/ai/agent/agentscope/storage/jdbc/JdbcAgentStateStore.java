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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 基于 JDBC 的 Agent 状态存储实现，通过 {@link SqlDialect} 适配不同数据库。
 *
 * <p>表：{@code jagent_state}（user_id + session_id + state_key 联合主键）
 * <p>List 类型的 state 序列化为 JSON 数组存储。
 */
public class JdbcAgentStateStore implements AgentStateStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentStateStore.class);
    private static final String ANON_USER = "__anon__";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcAgentStateStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        String uid = normalizeUser(userId);
        String json = toJson(value);
        String sql = dialect.stateUpsert();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            ps.setString(4, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save state: " + uid + "/" + sessionId + "/" + key, e);
        }
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        String uid = normalizeUser(userId);
        String json = toJson(values);
        // List 类型复用同一 upsert SQL，仅 state_type 标记不同；
        // 此处使用专用 list upsert 以区分 state_type
        String sql = dialect.stateListUpsert();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            ps.setString(4, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save list state: " + uid + "/" + sessionId + "/" + key, e);
        }
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        String uid = normalizeUser(userId);
        String sql = "SELECT state_value FROM jagent_state WHERE user_id = ? AND session_id = ? AND state_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromJson(rs.getString("state_value"), type));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get state: " + uid + "/" + sessionId + "/" + key, e);
        }
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        String uid = normalizeUser(userId);
        String sql = "SELECT state_value FROM jagent_state WHERE user_id = ? AND session_id = ? AND state_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("state_value");
                    return fromJsonList(json, itemType);
                }
                return List.of();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get list state: " + uid + "/" + sessionId + "/" + key, e);
        }
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        String uid = normalizeUser(userId);
        String sql = "SELECT 1 FROM jagent_state WHERE user_id = ? AND session_id = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check state exists: " + uid + "/" + sessionId, e);
        }
    }

    @Override
    public void delete(String userId, String sessionId) {
        String uid = normalizeUser(userId);
        String sql = "DELETE FROM jagent_state WHERE user_id = ? AND session_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete session state: " + uid + "/" + sessionId, e);
        }
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        String uid = normalizeUser(userId);
        String sql = "DELETE FROM jagent_state WHERE user_id = ? AND session_id = ? AND state_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete state key: " + uid + "/" + sessionId + "/" + key, e);
        }
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        String uid = normalizeUser(userId);
        String sql = "SELECT DISTINCT session_id FROM jagent_state WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> sessionIds = new LinkedHashSet<>();
                while (rs.next()) {
                    sessionIds.add(rs.getString("session_id"));
                }
                return sessionIds;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list session IDs for user: " + uid, e);
        }
    }

    @Override
    public void close() {
        // DataSource 由外部管理
    }

    // ==================== 工具方法 ====================

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? ANON_USER : userId;
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to " + type.getSimpleName(), e);
        }
    }

    private <T> List<T> fromJsonList(String json, Class<T> itemType) {
        try {
            List<Map<String, Object>> maps = MAPPER.readValue(json, new TypeReference<>() {});
            List<T> result = new ArrayList<>();
            for (Map<String, Object> map : maps) {
                String itemJson = MAPPER.writeValueAsString(map);
                result.add(MAPPER.readValue(itemJson, itemType));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON list", e);
        }
    }
}
