/**
 * jagent-agentscope 适配层 — 桥接 jagent-core 抽象与 AgentScope 2.0 实现。
 *
 * <p>设计原则：极薄适配，AgentScope 能做的全交给 AgentScope。
 * <ul>
 *   <li>{@code adapter/} — 接口桥接（Model、Agent、消息、上下文转换）</li>
 *   <li>{@code config/} — Spring Boot 自动装配（配置属性、Agent 工厂）</li>
 *   <li>{@code skill/} — Skill → AgentTool 适配</li>
 * </ul>
 */
package com.jrl.ai.agent.agentscope;
