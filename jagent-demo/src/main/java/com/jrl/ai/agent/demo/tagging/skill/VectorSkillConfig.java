package com.jrl.ai.agent.demo.tagging.skill;

import com.jrl.ai.agent.demo.tagging.client.VectorStorageClient;
import com.jrl.ai.agent.core.skill.DefaultSkillRegistry;
import com.jrl.ai.agent.core.skill.SkillRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储 Skill 配置 — 将 Milvus 操作注册为 Agent 可调用的 Skill。
 *
 * <p>通过 {@link SkillRegistry} 注册后，Agent 在执行过程中可动态调用这些 Skill，
 * 并通过 {@link com.jrl.ai.agent.agentscope.skill.SkillToolAdapter} 自动桥接为
 * AgentScope 的 AgentTool。
 */
@Configuration
public class VectorSkillConfig {

    @Bean
    public SkillRegistry vectorSkillRegistry(VectorStorageClient vectorClient) {
        SkillRegistry registry = new DefaultSkillRegistry();
        registry.register(new VectorUpsertSkill(vectorClient));
        registry.register(new VectorSearchSkill(vectorClient));
        registry.register(new VectorGetSkill(vectorClient));
        return registry;
    }
}
