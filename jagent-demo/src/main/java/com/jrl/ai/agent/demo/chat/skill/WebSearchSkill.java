package com.jrl.ai.agent.demo.chat.skill;

import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网搜索 Skill — 多源 fallback 设计。
 *
 * <p>依次尝试多个搜索源：DuckDuckGo → Bing 必应 → 360 搜索，
 * 只要任一源返回结果即采用，全部失败才报错。
 *
 * <p>为什么多源 fallback：
 * <ul>
 *   <li>不同地区/网络对各搜索引擎的可达性不同（国内 DDG 不稳定，Bing 通畅）</li>
 *   <li>避免单点失败，提升技能可靠性</li>
 *   <li>所有源都永久免费、无需 API Key</li>
 * </ul>
 */
public class WebSearchSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(WebSearchSkill.class);

    private static final int MAX_RESULTS = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    /** 单个源的最大尝试时间（含 DNS + connect + read） */
    private static final Duration PER_SOURCE_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;

    public WebSearchSkill() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "联网搜索工具（多源 fallback：DuckDuckGo / Bing 必应 / 360 搜索）。" +
               "当需要获取实时信息、新闻、最新事件或知识库中不存在的内容时使用。" +
               "输入参数：query（搜索关键词，建议简洁明确）。" +
               "返回搜索结果的标题、摘要和链接。";
    }

    @Override
    public double priority() {
        return 1.0;
    }

    @Override
    public SkillResult execute(SkillContext context) {
        long start = System.currentTimeMillis();
        log.info("[WebSearch] execute 被调用: parameters={}, input='{}'",
                context.parameters(), context.input());

        String query = (String) context.parameters().getOrDefault("query", "");
        if (query.isBlank()) {
            query = context.input() != null ? context.input() : "";
        }

        if (query.isBlank()) {
            log.warn("[WebSearch] query 为空，parameters={}, input='{}'", context.parameters(), context.input());
            return SkillResult.failure(name(), "搜索关键词为空", System.currentTimeMillis() - start);
        }

        log.info("[WebSearch] query='{}'", query);

        // 多源尝试，按顺序 fallback
        List<SearchSource> sources = List.of(
                new BingSearchSource(),
                new DuckDuckGoSearchSource(),
                new So360SearchSource()
        );

        List<String> errors = new ArrayList<>();
        for (SearchSource source : sources) {
            try {
                long sStart = System.currentTimeMillis();
                List<String> results = source.search(httpClient, query);
                long sDur = System.currentTimeMillis() - sStart;
                if (!results.isEmpty()) {
                    log.info("[WebSearch] source={} query='{}' results={} duration={}ms",
                            source.name(), query, results.size(), sDur);
                    return SkillResult.success(name(), formatOutput(source.name(), query, results),
                            System.currentTimeMillis() - start);
                } else {
                    log.info("[WebSearch] source={} query='{}' 返回空结果", source.name(), query);
                }
            } catch (Exception e) {
                log.warn("[WebSearch] source={} query='{}' 失败: {}", source.name(), query, e.getMessage());
                errors.add(source.name() + ": " + e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.error("[WebSearch] 所有源均失败 query='{}' errors={}", query, errors);
        return SkillResult.failure(name(),
                "所有搜索源均失败。" + String.join("; ", errors),
                duration);
    }

    private String formatOutput(String sourceName, String query, List<String> results) {
        StringBuilder output = new StringBuilder();
        output.append("[").append(sourceName).append("] 搜索「").append(query).append("」的结果：\n\n");
        for (int i = 0; i < results.size(); i++) {
            output.append(results.get(i));
            if (i < results.size() - 1) {
                output.append("\n---\n");
            }
        }
        return output.toString();
    }

    // ============================================================
    // 搜索源接口
    // ============================================================

    private interface SearchSource {
        String name();
        List<String> search(HttpClient client, String query) throws Exception;
    }

    // ============================================================
    // DuckDuckGo 源
    // ============================================================

    private static class DuckDuckGoSearchSource implements SearchSource {
        private static final String URL = "https://html.duckduckgo.com/html/";

        @Override
        public String name() {
            return "DuckDuckGo";
        }

        @Override
        public List<String> search(HttpClient client, String query) throws IOException, InterruptedException {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = URL + "?q=" + encoded + "&kl=cn-zh";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(PER_SOURCE_TIMEOUT)
                    .header("User-Agent", userAgent())
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Referer", "https://duckduckgo.com/")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return parseDdgHtml(response.body());
        }

        private List<String> parseDdgHtml(String html) {
            List<String> results = new ArrayList<>();
            Pattern titlePattern = Pattern.compile(
                    "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL
            );
            Pattern snippetPattern = Pattern.compile(
                    "<a[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL
            );
            Matcher m = titlePattern.matcher(html);
            int idx = 0;
            while (m.find() && idx < MAX_RESULTS) {
                String title = cleanHtml(m.group(2));
                if (title.isBlank()) continue;
                String link = normalizeUrl(decodeEntities(m.group(1)));
                String snippet = "";
                String tail = html.substring(m.end(), Math.min(m.end() + 3000, html.length()));
                Matcher sm = snippetPattern.matcher(tail);
                if (sm.find()) {
                    snippet = cleanHtml(sm.group(1));
                }
                results.add(formatItem(idx + 1, title, link, snippet));
                idx++;
            }
            return results;
        }
    }

    // ============================================================
    // Bing 必应源
    // ============================================================

    private static class BingSearchSource implements SearchSource {
        private static final String URL = "https://www.bing.com/search";

        @Override
        public String name() {
            return "Bing";
        }

        @Override
        public List<String> search(HttpClient client, String query) throws IOException, InterruptedException {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // setlang=zh-Hans 中文界面，cc=CN 中国区
            String url = URL + "?q=" + encoded + "&setlang=zh-Hans&cc=CN&count=" + MAX_RESULTS;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(PER_SOURCE_TIMEOUT)
                    .header("User-Agent", userAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return parseBingHtml(response.body());
        }

        /**
         * Bing HTML 搜索结果结构（2026）：
         * <ul>
         *   <li>容器：{@code <li class="b_algo">}</li>
         *   <li>标题链接：{@code <h2><a href="...">标题</a></h2>}</li>
         *   <li>摘要：{@code <p class="b_lineclamp2 ...">摘要</p>}</li>
         * </ul>
         */
        private List<String> parseBingHtml(String html) {
            List<String> results = new ArrayList<>();
            // 匹配 b_algo 列表项
            Pattern liPattern = Pattern.compile(
                    "<li[^>]*class=\"[^\"]*\\bb_algo\\b[^\"]*\"[^>]*>(.*?)</li>",
                    Pattern.DOTALL
            );
            Matcher liMatcher = liPattern.matcher(html);
            int idx = 0;
            while (liMatcher.find() && idx < MAX_RESULTS) {
                String block = liMatcher.group(1);
                String title = extractFirst(block, "<h2[^>]*>\\s*<a[^>]*>(.*?)</a>", 1);
                if (title == null) {
                    // 备用：b_algoheader
                    title = extractFirst(block, "<a[^>]*class=\"[^\"]*\\bb_algoheader\\b[^\"]*\"[^>]*>(.*?)</a>", 1);
                }
                if (title == null || title.isBlank()) continue;
                title = cleanHtml(title);

                String link = extractFirst(block, "<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"", 1);
                if (link == null) {
                    link = extractFirst(block, "<a[^>]*class=\"[^\"]*\\bb_algoheader\\b[^\"]*\"[^>]*href=\"([^\"]+)\"", 1);
                }
                link = normalizeUrl(link);

                // 摘要可能在 <p class="b_paractl"> 或 <p> 标签内
                String snippet = extractFirst(block, "<p[^>]*class=\"[^\"]*\\bb_lineclamp\\b[^\"]*\"[^>]*>(.*?)</p>", 1);
                if (snippet == null) {
                    snippet = extractFirst(block, "<p[^>]*>(.*?)</p>", 1);
                }
                if (snippet != null) {
                    snippet = cleanHtml(snippet);
                    if (snippet.length() > 250) snippet = snippet.substring(0, 250) + "...";
                }

                results.add(formatItem(idx + 1, title, link, snippet));
                idx++;
            }
            return results;
        }
    }

    // ============================================================
    // 360 搜索源（国内备用）
    // ============================================================

    private static class So360SearchSource implements SearchSource {
        private static final String URL = "https://www.so.com/s";

        @Override
        public String name() {
            return "360搜索";
        }

        @Override
        public List<String> search(HttpClient client, String query) throws IOException, InterruptedException {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = URL + "?q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(PER_SOURCE_TIMEOUT)
                    .header("User-Agent", userAgent())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return parseSo360Html(response.body());
        }

        /**
         * 360 搜索 HTML 结构：
         * <ul>
         *   <li>容器：{@code <li class="res-list">} 或 {@code <div class="result">}</li>
         *   <li>标题：{@code <h3 class="res-title"><a href="...">...</a></h3>}</li>
         *   <li>摘要：{@code <p class="res-desc">...</p>}</li>
         * </ul>
         */
        private List<String> parseSo360Html(String html) {
            List<String> results = new ArrayList<>();
            Pattern liPattern = Pattern.compile(
                    "<li[^>]*class=\"[^\"]*\\bres-list\\b[^\"]*\"[^>]*>(.*?)</li>",
                    Pattern.DOTALL
            );
            Matcher liMatcher = liPattern.matcher(html);
            int idx = 0;
            while (liMatcher.find() && idx < MAX_RESULTS) {
                String block = liMatcher.group(1);
                String title = extractFirst(block, "<h3[^>]*class=\"[^\"]*\\bres-title\\b[^\"]*\"[^>]*>\\s*<a[^>]*>(.*?)</a>", 1);
                if (title == null) {
                    title = extractFirst(block, "<a[^>]*>(.*?)</a>", 1);
                }
                if (title == null || title.isBlank()) continue;
                title = cleanHtml(title);

                String link = extractFirst(block, "<h3[^>]*class=\"[^\"]*\\bres-title\\b[^\"]*\"[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"", 1);
                if (link == null) {
                    link = extractFirst(block, "<a[^>]*href=\"(https?://[^\"]+)\"", 1);
                }
                link = normalizeUrl(link);

                String snippet = extractFirst(block, "<p[^>]*class=\"[^\"]*\\bres-desc\\b[^\"]*\"[^>]*>(.*?)</p>", 1);
                if (snippet != null) {
                    snippet = cleanHtml(snippet);
                    if (snippet.length() > 250) snippet = snippet.substring(0, 250) + "...";
                }

                results.add(formatItem(idx + 1, title, link, snippet));
                idx++;
            }
            return results;
        }
    }

    // ============================================================
    // 通用工具方法
    // ============================================================

    private static String userAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
               "AppleWebKit/537.36 (KHTML, like Gecko) " +
               "Chrome/120.0.0.0 Safari/537.36";
    }

    private static String formatItem(int idx, String title, String link, String snippet) {
        StringBuilder item = new StringBuilder();
        item.append(idx).append(". ").append(title);
        if (link != null && !link.isBlank()) {
            item.append("\n   链接: ").append(link);
        }
        if (snippet != null && !snippet.isBlank()) {
            item.append("\n   摘要: ").append(snippet);
        }
        return item.toString();
    }

    private static String extractFirst(String text, String regex, int group) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        if (m.find()) {
            return m.group(group);
        }
        return null;
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }

    private static String decodeEntities(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    private static String cleanHtml(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("<[^>]+>", "");
        cleaned = decodeEntities(cleaned);
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }
}
