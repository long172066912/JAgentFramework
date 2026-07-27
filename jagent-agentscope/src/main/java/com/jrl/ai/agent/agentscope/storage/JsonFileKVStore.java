package com.jrl.ai.agent.agentscope.storage;

import com.jrl.ai.agent.core.storage.KVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JSON 文件的 KV 存储实现 — 内存缓存 + 文件持久化。
 *
 * <p>数据以简单 JSON 格式存储在指定目录下，每个 key 对应一个文件。
 * 内存中维护 ConcurrentHashMap 缓存，写入时同步刷盘。
 */
public class JsonFileKVStore implements KVStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileKVStore.class);

    private final Path basePath;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    /**
     * 创建文件 KV 存储。
     *
     * @param basePath 数据存储目录
     */
    public JsonFileKVStore(Path basePath) {
        this.basePath = basePath;
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            log.warn("创建 KV 存储目录失败: {}", basePath, e);
        }
    }

    @Override
    public void put(String key, String value) {
        cache.put(key, value);
        flushToDisk(key, value);
    }

    @Override
    public Optional<String> get(String key) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void delete(String key) {
        cache.remove(key);
        deleteFromDisk(key);
    }

    @Override
    public boolean exists(String key) {
        ensureLoaded();
        return cache.containsKey(key);
    }

    private void ensureLoaded() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    loadFromDisk();
                    loaded = true;
                }
            }
        }
    }

    private void loadFromDisk() {
        try {
            if (!Files.exists(basePath)) return;
            try (var stream = Files.list(basePath)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            try {
                                String key = p.getFileName().toString().replace(".json", "");
                                String value = Files.readString(p).trim();
                                // 去除首尾引号（JSON 字符串格式）
                                if (value.startsWith("\"") && value.endsWith("\"")) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                cache.put(key, value);
                            } catch (IOException e) {
                                log.warn("读取 KV 文件失败: {}", p, e);
                            }
                        });
            }
            log.info("从磁盘加载 {} 条 KV 记录: {}", cache.size(), basePath);
        } catch (IOException e) {
            log.warn("扫描 KV 存储目录失败: {}", basePath, e);
        }
    }

    private void flushToDisk(String key, String value) {
        try {
            Path file = basePath.resolve(sanitize(key) + ".json");
            // 简单 JSON 字符串格式
            Files.writeString(file, "\"" + escapeJson(value) + "\"");
        } catch (IOException e) {
            log.warn("写入 KV 文件失败: key={}", key, e);
        }
    }

    private void deleteFromDisk(String key) {
        try {
            Path file = basePath.resolve(sanitize(key) + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除 KV 文件失败: key={}", key, e);
        }
    }

    private static String sanitize(String key) {
        return key.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
