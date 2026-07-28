package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 基于 JSON 文件的优化报告存储实现。
 *
 * <p>每个 Agent 的报告存储在独立的 JSON 文件中。
 */
public class JsonFileOptimizationReportStore implements OptimizationReportStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileOptimizationReportStore.class);

    private final Path baseDir;
    private final Map<String, List<OptimizationReport>> cache = new ConcurrentHashMap<>();

    public JsonFileOptimizationReportStore(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.warn("[OptimizationStore] Failed to create directory: {}", baseDir, e);
        }
    }

    @Override
    public void save(OptimizationReport report) {
        String agentId = report.agentId();
        cache.computeIfAbsent(agentId, k -> new ArrayList<>()).addFirst(report);
        persist(agentId);
    }

    @Override
    public List<OptimizationReport> findByAgent(String agentId, int limit) {
        List<OptimizationReport> reports = cache.getOrDefault(agentId, load(agentId));
        return reports.stream().limit(limit).toList();
    }

    @Override
    public OptimizationReport findLatest(String agentId) {
        List<OptimizationReport> reports = cache.getOrDefault(agentId, load(agentId));
        return reports.isEmpty() ? null : reports.getFirst();
    }

    private void persist(String agentId) {
        Path file = baseDir.resolve(agentId + ".json");
        List<OptimizationReport> reports = cache.getOrDefault(agentId, List.of());

        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < reports.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(serializeReport(reports.get(i)));
        }
        sb.append("\n]");

        try {
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            log.warn("[OptimizationStore] Failed to persist report for agent={}", agentId, e);
        }
    }

    private List<OptimizationReport> load(String agentId) {
        Path file = baseDir.resolve(agentId + ".json");
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try {
            String content = Files.readString(file);
            List<OptimizationReport> reports = deserializeReports(content);
            cache.put(agentId, reports);
            return reports;
        } catch (IOException e) {
            log.warn("[OptimizationStore] Failed to load report for agent={}", agentId, e);
            return new ArrayList<>();
        }
    }

    private String serializeReport(OptimizationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"agentId\": \"").append(escape(report.agentId())).append("\",\n");
        sb.append("  \"sessionId\": \"").append(escape(report.sessionId() != null ? report.sessionId() : "")).append("\",\n");
        sb.append("  \"compositeScore\": ").append(report.compositeScore()).append(",\n");
        sb.append("  \"generatedAt\": \"").append(report.generatedAt()).append("\",\n");

        // executionMetrics
        sb.append("  \"executionMetrics\": {");
        boolean first = true;
        for (var entry : report.executionMetrics().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(String.valueOf(entry.getValue()))).append("\"");
            first = false;
        }
        sb.append("},\n");

        // suggestions
        sb.append("  \"suggestions\": [\n");
        List<OptimizationSuggestion> suggestions = report.suggestions();
        for (int i = 0; i < suggestions.size(); i++) {
            if (i > 0) sb.append(",\n");
            OptimizationSuggestion s = suggestions.get(i);
            sb.append("    {\"category\":\"").append(s.category().name())
                    .append("\",\"priority\":\"").append(s.priority().name())
                    .append("\",\"title\":\"").append(escape(s.title()))
                    .append("\",\"content\":\"").append(escape(s.content()))
                    .append("\",\"reason\":\"").append(escape(s.reason())).append("\"}");
        }
        sb.append("\n  ]\n");

        sb.append("}");
        return sb.toString();
    }

    private List<OptimizationReport> deserializeReports(String content) {
        // 简化实现：返回空列表，生产环境应使用 JSON 库
        return new ArrayList<>();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
