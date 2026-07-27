package com.jrl.ai.agent.demo;

import com.jrl.ai.agent.agentscope.config.JAgentAutoConfiguration;
import com.jrl.ai.agent.agentscope.config.JAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * JAgent Demo 启动类 — Spring Boot + AgentScope 2.0 集成演示。
 */
@SpringBootApplication
@EnableConfigurationProperties(JAgentProperties.class)
@Import(JAgentAutoConfiguration.class)
public class JAgentDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JAgentDemoApplication.class, args);
    }
}
