package com.jrl.ai.agent.agentscope.prompt;

import com.jrl.ai.agent.core.prompt.PromptTemplate;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 简单提示词模板 — 基于 {@code ${variable}} 占位符的字符串替换实现。
 *
 * <p>示例：
 * <pre>{@code
 * PromptTemplate tpl = new SimplePromptTemplate("greeting", "你好，${name}！欢迎使用 ${product}。");
 * String result = tpl.render(Map.of("name", "张三", "product", "JAgent"));
 * // => "你好，张三！欢迎使用 JAgent。"
 * }</pre>
 */
public class SimplePromptTemplate implements PromptTemplate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final String name;
    private final String template;
    private final String version;
    private final Set<String> variableNames;

    /**
     * 创建提示词模板。
     *
     * @param name     模板名称
     * @param template 模板内容（含 {@code ${variable}} 占位符）
     */
    public SimplePromptTemplate(String name, String template) {
        this(name, template, "latest");
    }

    /**
     * 创建带版本号的提示词模板。
     *
     * @param name     模板名称
     * @param template 模板内容
     * @param version  版本号
     */
    public SimplePromptTemplate(String name, String template, String version) {
        this.name = name;
        this.template = template;
        this.version = version;
        this.variableNames = extractVariableNames(template);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public String render(Map<String, Object> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    value != null ? value.toString() : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public Set<String> variableNames() {
        return variableNames;
    }

    /**
     * 获取模板版本号。
     *
     * @return 版本号
     */
    public String version() {
        return version;
    }

    private static Set<String> extractVariableNames(String template) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        return matcher.results()
                .map(m -> m.group(1))
                .collect(Collectors.toSet());
    }
}
