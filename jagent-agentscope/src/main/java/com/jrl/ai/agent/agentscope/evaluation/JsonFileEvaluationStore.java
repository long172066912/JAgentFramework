package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.evaluation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * JSON 文件评测存储 — 将评测结果持久化为 JSON 文件。
 *
 * <p>存储路径：{@code {workspace}/evaluation/{agentId}/}
 * 每个 Agent 一个目录，文件名格式：{@code {timestamp}-{evalId}.json}
 */
public class JsonFileEvaluationStore implements EvaluationStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileEvaluationStore.class);

    private final Path basePath;

    /** 内存缓存，加速查询 */
    private final List<EvaluationResult> cache = new CopyOnWriteArrayList<>();

    /**
     * 创建 JSON 文件存储。
     *
     * @param basePath 存储根目录
     */
    public JsonFileEvaluationStore(Path basePath) {
        this.basePath = basePath;
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            log.warn("[EvaluationStore] Failed to create directory: {}", basePath, e);
        }
    }

    @Override
    public void save(EvaluationResult result) {
        cache.add(result);

        Path agentDir = basePath.resolve(result.agentId());
        try {
            Files.createDirectories(agentDir);
            String filename = result.timestamp().toEpochMilli() + "-" + result.evalId() + ".json";
            Path filePath = agentDir.resolve(filename);
            String json = toJson(result);
            Files.writeString(filePath, json);
            log.debug("[EvaluationStore] Saved: {}", filePath);
        } catch (IOException e) {
            log.error("[EvaluationStore] Failed to save evaluation: {}", result.evalId(), e);
        }
    }

    @Override
    public List<EvaluationResult> findByAgent(String agentId, int limit) {
        return cache.stream()
                .filter(r -> r.agentId().equals(agentId))
                .sorted(Comparator.comparing(EvaluationResult::timestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<EvaluationResult> findBySession(String sessionId) {
        return cache.stream()
                .filter(r -> sessionId.equals(r.sessionId()))
                .sorted(Comparator.comparing(EvaluationResult::timestamp).reversed())
                .toList();
    }

    @Override
    public EvaluationAggregate getAggregate(String agentId, EvaluationDimension dimension, long windowMs) {
        Instant cutoff = windowMs > 0 ? Instant.now().minusMillis(windowMs) : Instant.MIN;

        List<DimensionScore> matching = cache.stream()
                .filter(r -> r.agentId().equals(agentId))
                .filter(r -> r.timestamp().isAfter(cutoff))
                .filter(r -> r.scores().containsKey(dimension))
                .map(r -> r.scores().get(dimension))
                .toList();

        if (matching.isEmpty()) {
            return new EvaluationAggregate(agentId, dimension, 0.0, 0.0, 0.0, 0, windowMs);
        }

        double sum = matching.stream().mapToDouble(DimensionScore::score).sum();
        double min = matching.stream().mapToDouble(DimensionScore::score).min().orElse(0.0);
        double max = matching.stream().mapToDouble(DimensionScore::score).max().orElse(0.0);

        return new EvaluationAggregate(
                agentId, dimension,
                sum / matching.size(), min, max,
                matching.size(), windowMs
        );
    }

    /**
     * 简单的 JSON 序列化（避免引入额外依赖）。
     */
    private String toJson(EvaluationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"evalId\": \"").append(result.evalId()).append("\",\n");
        sb.append("  \"agentId\": \"").append(result.agentId()).append("\",\n");
        sb.append("  \"sessionId\": \"").append(result.sessionId() != null ? result.sessionId() : "").append("\",\n");
        sb.append("  \"compositeScore\": ").append(result.compositeScore()).append(",\n");
        sb.append("  \"timestamp\": \"").append(result.timestamp()).append("\",\n");
        sb.append("  \"input\": ").append(jsonString(result.input())).append(",\n");
        sb.append("  \"output\": ").append(jsonString(result.output())).append(",\n");
        sb.append("  \"scores\": {\n");

        List<String> scoreEntries = new ArrayList<>();
        for (var entry : result.scores().entrySet()) {
            DimensionScore ds = entry.getValue();
            scoreEntries.add(String.format(
                    "    \"%s\": {\"score\": %.4f, \"level\": \"%s\", \"reason\": %s}",
                    entry.getKey().name(), ds.score(), ds.level().name(),
                    jsonString(ds.reason())
            ));
        }
        sb.append(String.join(",\n", scoreEntries));
        sb.append("\n  }\n}");
        return sb.toString();
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
