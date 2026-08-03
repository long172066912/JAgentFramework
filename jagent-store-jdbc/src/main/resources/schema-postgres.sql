-- JAgent PostgreSQL 分布式存储建表脚本

-- KV 存储
CREATE TABLE IF NOT EXISTS jagent_kv (
    kv_key VARCHAR(512) PRIMARY KEY,
    kv_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 记忆存储
CREATE TABLE IF NOT EXISTS jagent_memory (
    namespace VARCHAR(256) NOT NULL,
    mem_key VARCHAR(512) NOT NULL,
    mem_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, mem_key)
);

CREATE INDEX IF NOT EXISTS idx_namespace ON jagent_memory (namespace);

-- Agent 状态存储
CREATE TABLE IF NOT EXISTS jagent_state (
    user_id VARCHAR(256) NOT NULL,
    session_id VARCHAR(256) NOT NULL,
    state_key VARCHAR(256) NOT NULL,
    state_value TEXT NOT NULL,
    state_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, session_id, state_key)
);

CREATE INDEX IF NOT EXISTS idx_user_session ON jagent_state (user_id, session_id);
