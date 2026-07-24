package com.jrl.ai.agent.core.skill;

import com.jrl.ai.agent.core.context.AgentContext;

import java.util.Map;

/**
 * Skill 执行上下文
 */
public record SkillContext(
        String skillName,
        String input,
        AgentContext agentContext,
        Map<String, Object> parameters
) {
}
