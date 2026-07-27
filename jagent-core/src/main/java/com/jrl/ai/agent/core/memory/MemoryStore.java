package com.jrl.ai.agent.core.memory;

import java.util.List;
import java.util.Optional;

/**
 * 记忆存储接口 — Agent 的长期/短期记忆持久化抽象。
 *
 * <p>支持按 key 存取记忆条目，按 namespace 分类管理。
 * 具体实现可以是内存、文件、数据库或向量存储。
 *
 * @see MemoryInterceptor
 */
public interface MemoryStore {

    /**
     * 存储一条记忆。
     *
     * @param namespace 命名空间（如 sessionId、userId）
     * @param key       记忆键
     * @param value     记忆内容
     */
    void put(String namespace, String key, String value);

    /**
     * 查询一条记忆。
     *
     * @param namespace 命名空间
     * @param key       记忆键
     * @return 记忆内容，不存在时返回 empty
     */
    Optional<String> get(String namespace, String key);

    /**
     * 列出命名空间下的所有记忆键。
     *
     * @param namespace 命名空间
     * @return 键列表
     */
    List<String> keys(String namespace);

    /**
     * 删除一条记忆。
     *
     * @param namespace 命名空间
     * @param key       记忆键
     */
    void remove(String namespace, String key);

    /**
     * 清空命名空间下的所有记忆。
     *
     * @param namespace 命名空间
     */
    void clear(String namespace);
}
