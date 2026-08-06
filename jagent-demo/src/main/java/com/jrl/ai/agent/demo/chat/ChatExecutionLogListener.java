package com.jrl.ai.agent.demo.chat;

import com.jrl.ai.agent.core.agent.AgentExecutionEvent;
import com.jrl.ai.agent.core.agent.ScopedAgentExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能助手执行日志监听器 — 记录「智能助手」每次执行的开始与结束。
 *
 * <p>继承 {@link ScopedAgentExecutionListener} 按配置键名（agentKey=chat）绑定，
 * 框架分发时自动过滤，只会收到智能助手的事件，与展示名称无关。
 */
public class ChatExecutionLogListener extends ScopedAgentExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ChatExecutionLogListener.class);

    /**
     * 绑定智能助手配置键名（application.yml 中 jagent.agents 下的 key）。
     */
    public ChatExecutionLogListener() {
        super("chat");
    }

    @Override
    public void onExecutionStart(AgentExecutionEvent event) {
        log.info("[智能助手] ▶ 执行开始 | mode={} | session={} | user={} | input={}",
                event.mode(),
                event.context() != null ? event.context().sessionId() : "-",
                event.context() != null ? event.context().userId() : "-",
                truncate(event.input() != null ? event.input().content() : ""));
    }

    @Override
    public void onExecutionEnd(AgentExecutionEvent event) {
        if (event.isSuccess()) {
            log.info("[智能助手] ✔ 执行结束 | mode={} | 耗时={}ms", event.mode(), event.durationMs());
        } else {
            log.warn("[智能助手] ✘ 执行失败 | mode={} | 耗时={}ms | error={}",
                    event.mode(), event.durationMs(),
                    event.error() != null ? event.error().getMessage() : "未知错误");
        }
    }

    /**
     * 截断过长输入，避免日志刷屏。
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}
