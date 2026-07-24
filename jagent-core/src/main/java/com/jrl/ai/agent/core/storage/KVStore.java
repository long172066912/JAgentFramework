package com.jrl.ai.agent.core.storage;

import java.util.Optional;

/**
 * 通用存储抽象 — KV 存储
 */
public interface KVStore {

    void put(String key, String value);

    Optional<String> get(String key);

    void delete(String key);

    boolean exists(String key);
}
