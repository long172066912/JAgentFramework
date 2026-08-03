-- JAgent MySQL 分布式存储建表脚本

-- KV 存储
CREATE TABLE IF NOT EXISTS jagent_kv (
    kv_key VARCHAR(512) PRIMARY KEY,
    kv_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 记忆存储
CREATE TABLE IF NOT EXISTS jagent_memory (
    namespace VARCHAR(256) NOT NULL,
    mem_key VARCHAR(512) NOT NULL,
    mem_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, mem_key),
    INDEX idx_namespace (namespace)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agent 状态存储
CREATE TABLE IF NOT EXISTS jagent_state (
    user_id VARCHAR(256) NOT NULL,
    session_id VARCHAR(256) NOT NULL,
    state_key VARCHAR(256) NOT NULL,
    state_value TEXT NOT NULL,
    state_type ENUM('SINGLE', 'LIST') NOT NULL DEFAULT 'SINGLE',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, session_id, state_key),
    INDEX idx_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
