package com.jrl.ai.agent.core.skill;

import com.jrl.ai.agent.core.context.AgentContext;

import java.util.Map;

/**
 * Skill 执行上下文 — 携带技能执行所需的全部输入信息。
 *
 * @see Skill#execute(SkillContext)
 */
public record SkillContext(
        /** 当前执行的技能名称 */
        String skillName,
        /** 技能输入文本 */
        String input,
        /** Agent 运行时上下文（透传会话、用户等信息） */
        AgentContext agentContext,
        /** 技能特定参数（由 LLM 工具调用解析而来） */
        Map<String, Object> parameters
) {
}
