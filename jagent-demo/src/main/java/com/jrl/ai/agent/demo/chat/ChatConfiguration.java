package com.jrl.ai.agent.demo.chat;

import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.demo.chat.skill.WebSearchSkill;
import com.jrl.ai.agent.demo.chat.skill.KnowledgeSearchSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能对话模块配置 — 注册对话相关 Bean。
 */
@Configuration
public class ChatConfiguration {

    /**
     * 知识检索 Skill（模拟实现，写死数据）。
     */
    @Bean
    public Skill knowledgeSearchSkill() {
        return new KnowledgeSearchSkill();
    }

    /**
     * 联网搜索 Skill — 多源 fallback（Bing → DuckDuckGo → 360），永久免费、无需 API Key。
     */
    @Bean
    public Skill webSearchSkill() {
        return new WebSearchSkill();
    }
}
