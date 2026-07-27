package com.jrl.ai.agent.core.memory;

/**
 * 记忆操作拦截器 — 在 MemoryStore 的读写操作前后插入自定义逻辑。
 *
 * <p>典型用途：监控记忆读写耗时、审计记忆变更、缓存预热等。
 * 所有方法均提供默认空实现，实现方按需覆写。
 *
 * @see MemoryStore
 */
public interface MemoryInterceptor {

    /**
     * 记忆写入前调用（前置通知）。
     *
     * @param store     目标存储
     * @param namespace 命名空间
     * @param key       记忆键
     * @param value     记忆内容
     */
    default void beforePut(MemoryStore store, String namespace, String key, String value) {}

    /**
     * 记忆写入后调用（后置通知）。
     *
     * @param store     目标存储
     * @param namespace 命名空间
     * @param key       记忆键
     * @param value     记忆内容
     */
    default void afterPut(MemoryStore store, String namespace, String key, String value) {}

    /**
     * 记忆读取前调用（前置通知）。
     *
     * @param store     目标存储
     * @param namespace 命名空间
     * @param key       记忆键
     */
    default void beforeGet(MemoryStore store, String namespace, String key) {}

    /**
     * 记忆读取后调用（后置通知）。
     *
     * @param store     目标存储
     * @param namespace 命名空间
     * @param key       记忆键
     * @param value     读取结果（可能为空）
     */
    default void afterGet(MemoryStore store, String namespace, String key, String value) {}

    /**
     * 记忆删除前调用（前置通知）。
     *
     * @param store     目标存储
     * @param namespace 命名空间
     * @param key       记忆键
     */
    default void beforeRemove(MemoryStore store, String namespace, String key) {}

    /**
     * 记忆删除后调用（后置通知）。
     *
     * @param store     目标存储
     * @param namespace 命名空间
     * @param key       记忆键
     */
    default void afterRemove(MemoryStore store, String namespace, String key) {}
}
