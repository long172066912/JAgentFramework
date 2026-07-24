package com.jrl.ai.agent.core.async;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.io.ChatMessage;

/**
 * 流式输出回调
 */
public interface StreamCallback {

    /**
     * 收到文本片段
     */
    void onDelta(String delta);

    /**
     * 完成
     */
    void onComplete(String fullOutput);

    /**
     * 出错
     */
    void onError(Throwable error);
}
