package com.jrl.ai.agent.core.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 记忆存储 — Agent 的记忆持久化抽象
 */
public interface MemoryStore {

    /**
     * 保存记忆
     */
    void save(MemoryEntry entry);

    /**
     * 按 key 查找
     */
    Optional<MemoryEntry> get(String agentId, String key);

    /**
     * 按 Agent 查询所有记忆
     */
    List<MemoryEntry> list(String agentId);

    /**
     * 删除记忆
     */
    void delete(String agentId, String key);

    /**
     * 清空 Agent 的所有记忆
     */
    void clear(String agentId);
}
