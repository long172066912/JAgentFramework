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

import com.jrl.ai.agent.agentscope.storage.DistributedStoreProvider;
import com.jrl.ai.agent.agentscope.storage.jdbc.dialect.H2Dialect;
import com.jrl.ai.agent.agentscope.storage.jdbc.dialect.MySqlDialect;
import com.jrl.ai.agent.agentscope.storage.jdbc.dialect.PostgresDialect;
import com.jrl.ai.agent.core.memory.MemoryStore;
import com.jrl.ai.agent.core.storage.KVStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.core.state.AgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;

/**
 * JDBC 分布式存储提供器 — 通过 SPI 自动发现，支持 MySQL / PostgreSQL / H2。
 *
 * <p>支持的配置项：
 * <ul>
 *   <li>jdbc-url — JDBC 连接 URL（必填，用于自动检测数据库类型）</li>
 *   <li>username — 数据库用户名</li>
 *   <li>password — 数据库密码</li>
 *   <li>driver-class-name — JDBC 驱动类名（可选，默认自动检测）</li>
 *   <li>dialect — 显式指定方言名称：mysql / postgresql / h2（可选，默认从 jdbc-url 推断）</li>
 *   <li>maximum-pool-size — 连接池大小（默认 10）</li>
 * </ul>
 */
public class JdbcStoreProvider implements DistributedStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(JdbcStoreProvider.class);

    private volatile DataSource dataSource;
    private volatile SqlDialect dialect;

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public KVStore createKVStore(Map<String, String> config) {
        return new JdbcKVStore(getDataSource(config), getDialect(config));
    }

    @Override
    public MemoryStore createMemoryStore(Map<String, String> config) {
        return new JdbcMemoryStore(getDataSource(config), getDialect(config));
    }

    @Override
    public AgentStateStore createAgentStateStore(Map<String, String> config) {
        return new JdbcAgentStateStore(getDataSource(config), getDialect(config));
    }

    // ==================== 内部方法 ====================

    /**
     * 根据 jdbc-url 自动检测数据库类型，或使用显式配置的 dialect。
     */
    private SqlDialect getDialect(Map<String, String> config) {
        if (dialect != null) {
            return dialect;
        }
        synchronized (this) {
            if (dialect != null) {
                return dialect;
            }
            // 优先使用显式配置
            String explicit = config.get("dialect");
            if (explicit != null && !explicit.isBlank()) {
                dialect = resolveDialect(explicit);
            } else {
                // 从 jdbc-url 推断
                String jdbcUrl = config.get("jdbc-url");
                if (jdbcUrl == null) {
                    throw new IllegalArgumentException("jdbc-url is required to auto-detect database dialect");
                }
                dialect = detectDialect(jdbcUrl);
            }
            log.info("JDBC dialect selected: {}", dialect.name());
            return dialect;
        }
    }

    private static SqlDialect resolveDialect(String name) {
        return switch (name.toLowerCase()) {
            case "mysql" -> MySqlDialect.INSTANCE;
            case "postgresql", "postgres" -> PostgresDialect.INSTANCE;
            case "h2" -> H2Dialect.INSTANCE;
            default -> throw new IllegalArgumentException(
                    "Unsupported dialect: " + name + ". Supported: mysql, postgresql, h2");
        };
    }

    /**
     * 从 JDBC URL 推断方言。
     * <ul>
     *   <li>jdbc:mysql://... → MySQL</li>
     *   <li>jdbc:postgresql://... → PostgreSQL</li>
     *   <li>jdbc:h2://... → H2</li>
     * </ul>
     */
    private static SqlDialect detectDialect(String jdbcUrl) {
        String url = jdbcUrl.toLowerCase();
        if (url.contains(":mysql:")) {
            return MySqlDialect.INSTANCE;
        } else if (url.contains(":postgresql:")) {
            return PostgresDialect.INSTANCE;
        } else if (url.contains(":h2:")) {
            return H2Dialect.INSTANCE;
        }
        throw new IllegalArgumentException(
                "Cannot auto-detect dialect from jdbc-url: " + jdbcUrl
                        + ". Please set 'dialect' config explicitly (mysql/postgresql/h2).");
    }

    /**
     * 获取或创建 HikariCP DataSource（懒初始化，线程安全）。
     */
    private DataSource getDataSource(Map<String, String> config) {
        if (dataSource != null) {
            return dataSource;
        }
        synchronized (this) {
            if (dataSource != null) {
                return dataSource;
            }
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.get("jdbc-url"));
            hikariConfig.setUsername(config.get("username"));
            hikariConfig.setPassword(config.get("password"));

            String driverClassName = config.get("driver-class-name");
            if (driverClassName != null && !driverClassName.isBlank()) {
                hikariConfig.setDriverClassName(driverClassName);
            }

            String maxPoolSize = config.get("maximum-pool-size");
            if (maxPoolSize != null) {
                hikariConfig.setMaximumPoolSize(Integer.parseInt(maxPoolSize));
            } else {
                hikariConfig.setMaximumPoolSize(10);
            }

            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30_000);
            hikariConfig.setIdleTimeout(600_000);
            hikariConfig.setMaxLifetime(1800_000);

            dataSource = new HikariDataSource(hikariConfig);
            log.info("JDBC connection pool established: {}", config.get("jdbc-url"));
            return dataSource;
        }
    }
}
