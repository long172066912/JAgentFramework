package com.jrl.ai.agent.core.storage;

import java.util.Optional;

/**
 * 通用 KV 存储抽象 — 提供简单的键值对持久化能力。
 *
 * <p>用于 Agent 状态存储、会话持久化、配置缓存等场景。
 * 具体实现可对接本地文件、Redis、数据库等。
 */
public interface KVStore {

    /**
     * 写入键值对。
     *
     * @param key   键
     * @param value 值
     */
    void put(String key, String value);

    /**
     * 读取指定键的值。
     *
     * @param key 键
     * @return 对应的值，不存在时返回 {@link Optional#empty()}
     */
    Optional<String> get(String key);

    /**
     * 删除指定键。
     *
     * @param key 键
     */
    void delete(String key);

    /**
     * 判断指定键是否存在。
     *
     * @param key 键
     * @return 若存在则返回 {@code true}
     */
    boolean exists(String key);
}
