package com.jrl.ai.agent.agentscope.skill;

import com.jrl.ai.agent.agentscope.adapter.ContextConverter;
import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
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
        // 通用参数 schema — 接受任意 input 字段
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> inputProp = new LinkedHashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "技能输入文本");
        properties.put("input", inputProp);
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("input"));
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
            // 提取输入
            Map<String, Object> input = param.getInput();
            String inputText = input != null ? String.valueOf(input.getOrDefault("input", "")) : "";

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

            // 执行 Skill
            SkillResult result = skill.execute(skillCtx);

            // 转为 ToolResultBlock
            if (result.success()) {
                return ToolResultBlock.text(result.output());
            } else {
                return ToolResultBlock.error(result.output() != null ? result.output() : "Skill 执行失败");
            }
        });
    }
}
