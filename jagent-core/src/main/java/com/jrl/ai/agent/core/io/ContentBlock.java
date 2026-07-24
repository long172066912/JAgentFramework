package com.jrl.ai.agent.core.io;

/**
 * 内容块 — 消息的最小组成单元（文本、图片、工具调用结果等）
 */
public sealed interface ContentBlock permits TextBlock, ImageBlock, ToolCallBlock, ToolResultBlock {

    String type();
}
