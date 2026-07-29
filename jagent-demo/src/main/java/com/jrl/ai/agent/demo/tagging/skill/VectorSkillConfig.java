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

    /**
     * 注册向量存储相关的 Skill 到 SkillRegistry。
     *
     * <p>注册后 Agent 可通过 ReAct 推理循环动态调用以下 Skill：
     * <ul>
     *   <li>{@link VectorUpsertSkill} — 批量写入向量</li>
     *   <li>{@link VectorSearchSkill} — 相似向量检索</li>
     *   <li>{@link VectorGetSkill} — 批量查询向量</li>
     * </ul>
     *
     * @param vectorClient 向量存储客户端
     * @return 已注册所有向量 Skill 的 SkillRegistry
     */
    @Bean
    public SkillRegistry vectorSkillRegistry(VectorStorageClient vectorClient) {
        SkillRegistry registry = new DefaultSkillRegistry();
        registry.register(new VectorUpsertSkill(vectorClient));
        registry.register(new VectorSearchSkill(vectorClient));
        registry.register(new VectorGetSkill(vectorClient));
        return registry;
    }

    /**
     * 类目层级推断 Skill — 根据标签类目名称推断标签层级。
     *
     * <p>此 Skill 供业务代码直接调用（非 Agent 工具调用），
     * 用于将 LLM 输出的类目名称映射为标签层级（1/2/3）。
     *
     * @return CategoryLevelSkill 实例
     */
    @Bean
    public CategoryLevelSkill categoryLevelSkill() {
        return new CategoryLevelSkill();
    }
}
