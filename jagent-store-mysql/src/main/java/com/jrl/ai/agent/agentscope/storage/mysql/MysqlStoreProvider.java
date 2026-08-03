/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jrl.ai.agent.agentscope.storage.mysql;

import com.jrl.ai.agent.agentscope.storage.DistributedStoreProvider;
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
 * MySQL 分布式存储提供器 — 通过 SPI 自动发现。
 *
 * <p>支持的配置项：
 * <ul>
 *   <li>jdbc-url — JDBC 连接 URL（必填）</li>
 *   <li>username — 数据库用户名</li>
 *   <li>password — 数据库密码</li>
 *   <li>driver-class-name — JDBC 驱动类名（可选，默认自动检测）</li>
 *   <li>maximum-pool-size — 连接池大小（默认 10）</li>
 * </ul>
 */
public class MysqlStoreProvider implements DistributedStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(MysqlStoreProvider.class);

    private volatile DataSource dataSource;

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public KVStore createKVStore(Map<String, String> config) {
        return new MysqlKVStore(getDataSource(config));
    }

    @Override
    public MemoryStore createMemoryStore(Map<String, String> config) {
        return new MysqlMemoryStore(getDataSource(config));
    }

    @Override
    public AgentStateStore createAgentStateStore(Map<String, String> config) {
        return new MysqlAgentStateStore(getDataSource(config));
    }

    /**
     * 获取或创建 HikariCP DataSource（懒初始化，线程安全）。
     */
    private DataSource getDataSource(Map<String, String> config) {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
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
                    log.info("MySQL connection pool established: {}", config.get("jdbc-url"));
                }
            }
        }
        return dataSource;
    }
}
