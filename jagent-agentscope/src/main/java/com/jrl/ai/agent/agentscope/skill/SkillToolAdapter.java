package com.jrl.ai.agent.agentscope.skill;

import com.jrl.ai.agent.agentscope.adapter.ContextConverter;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 工具适配器 — 将 jagent-core {@link Skill} 包装为 AgentScope {@link AgentTool}。
 *
 * <p>使 jagent 定义的 Skill 能在 AgentScope 的 ReAct 推理循环中被 LLM 调用。
 * 参数通过 JSON 对象传递，Skill 执行结果转为 ToolResultBlock 返回。
 */
public class SkillToolAdapter implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SkillToolAdapter.class);

    private final Skill skill;

    /**
     * 创建适配器，包装 jagent Skill。
     *
     * @param skill jagent Skill 实例
     */
    public SkillToolAdapter(Skill skill) {
        this.skill = skill;
    }

    @Override
    public String getName() {
        return skill.name();
    }

    @Override
    public String getDescription() {
        return skill.description();
    }

    @Override
    public Map<String, Object> getParameters() {
        // 通用参数 schema — 接受 input 或 query 字段，additionalProperties 允许 LLM 传递任意参数
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> inputProp = new LinkedHashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "技能输入文本");
        properties.put("input", inputProp);

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索关键词或查询参数");
        properties.put("query", queryProp);

        schema.put("properties", properties);
        schema.put("required", java.util.List.of());
        schema.put("additionalProperties", true);
        return schema;
    }

    /**
     * 异步执行技能调用。
     *
     * <p>从 ToolCallParam 中提取输入参数，构造 SkillContext，
     * 调用 jagent Skill 的 execute 方法，将结果转为 ToolResultBlock。
     *
     * @param param AgentScope 工具调用参数
     * @return 工具执行结果的 Mono
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            // 提取输入 — 兼容 LLM 多种字段命名（query / input / text / q）
            Map<String, Object> input = param.getInput();
            String inputText = "";
            if (input != null) {
                // 按优先级尝试多个常见字段名
                for (String key : new String[]{"query", "input", "text", "q", "keyword", "search_query"}) {
                    Object val = input.get(key);
                    if (val != null && !String.valueOf(val).isEmpty()) {
                        inputText = String.valueOf(val);
                        break;
                    }
                }
                // 如果还没有找到，取第一个非空字符串值
                if (inputText.isEmpty()) {
                    for (Object val : input.values()) {
                        if (val != null && !String.valueOf(val).isEmpty()) {
                            inputText = String.valueOf(val);
                            break;
                        }
                    }
                }
            }

            // 构造 SkillContext
            RuntimeContext asCtx = param.getRuntimeContext();
            var jagentCtx = ContextConverter.toJAgent(asCtx);

            // 传递 agentId 供评分拦截器使用
            if (param.getAgent() != null && param.getAgent().getAgentId() != null) {
                jagentCtx.put("agentId", param.getAgent().getAgentId());
            }

            SkillContext skillCtx = new SkillContext(
                    skill.name(), inputText, jagentCtx,
                    input != null ? input : Map.of()
            );

            log.info("[SkillToolAdapter] 调用 skill={} inputText='{}' parameters={}",
                    skill.name(), inputText, skillCtx.parameters());

            // 执行 Skill
            SkillResult result = skill.execute(skillCtx);

            // 转为 ToolResultBlock — 必须保留 tool_call_id 以便 OpenAI 格式验证
            // 重要：ToolResultBlock.text/error 工厂会把 id 设为 null，会导致
            // OpenAI API 返回 "messages with role 'tool' must be a response to a preceeding tool_calls" 错误
            io.agentscope.core.message.ToolUseBlock toolUse = param.getToolUseBlock();
            String toolCallId = toolUse != null ? toolUse.getId() : "";
            String toolName = toolUse != null ? toolUse.getName() : skill.name();

            if (result.success()) {
                return ToolResultBlock.of(toolCallId, toolName,
                        io.agentscope.core.message.TextBlock.builder()
                                .text(result.output() != null ? result.output() : "")
                                .build());
            } else {
                return ToolResultBlock.of(toolCallId, toolName,
                        io.agentscope.core.message.TextBlock.builder()
                                .text(result.output() != null ? result.output() : "Skill 执行失败")
                                .build());
            }
        });
    }
}
