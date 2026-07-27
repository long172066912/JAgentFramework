/**
 * JAgent 核心框架 — AI Agent 基础抽象与扩展点。
 *
 * <p>本模块是纯抽象层，定义 AI Agent 通用能力的接口、实体与 SPI 扩展点，
 * 不依赖任何第三方框架，仅依赖 JDK 和 SLF4J。
 *
 * <h3>核心包结构</h3>
 * <ul>
 *   <li>{@code agent} — Agent 核心抽象、生命周期、注册表</li>
 *   <li>{@code context} — 运行时上下文</li>
 *   <li>{@code feedback} — 反馈机制（提示词反馈 / Skill 反馈）</li>
 *   <li>{@code io} — 消息模型（ChatMessage / MessageRole）</li>
 *   <li>{@code model} — LLM 模型抽象与注册表（支持多模型混用）</li>
 *   <li>{@code plan} — GOAP 风格规划（Goal / Plan / Planner）</li>
 *   <li>{@code prompt} — 提示词模板管理</li>
 *   <li>{@code retrieval} — RAG 检索抽象</li>
 *   <li>{@code router} — 任务路由</li>
 *   <li>{@code skill} — 技能与工具抽象</li>
 *   <li>{@code storage} — KV 存储抽象</li>
 *   <li>{@code task} — 任务模型与传输契约</li>
 * </ul>
 *
 * <p>具体实现由 {@code jagent-agentscope} 模块桥接 AgentScope 2.0。
 */
package com.jrl.ai.agent.core;
