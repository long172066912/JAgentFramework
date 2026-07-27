package com.jrl.ai.agent.demo.tagging.mq;

import com.jrl.ai.agent.demo.tagging.model.TaggingCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MQ 回执生产者 — 发送打标任务处理结果。
 *
 * <p>对应 AI Agent 交互协议中的「任务处理回执」。
 * 生产环境替换为真实 Kafka/RocketMQ 生产者。
 */
@Component
public class CallbackProducer {

    private static final Logger log = LoggerFactory.getLogger(CallbackProducer.class);

    /** 已发送的回执列表（模拟 MQ，开发测试用） */
    private final List<TaggingCallback> sentCallbacks = new CopyOnWriteArrayList<>();

    /**
     * 发送成功回执。
     *
     * @param callback 回执内容
     */
    public void sendSuccess(TaggingCallback callback) {
        sentCallbacks.add(callback);
        log.info("[MQ] 发送成功回执 taskId={}, processTime={}ms",
                callback.taskId(), callback.processTime());
    }

    /**
     * 发送失败回执。
     *
     * @param callback 回执内容
     */
    public void sendFail(TaggingCallback callback) {
        sentCallbacks.add(callback);
        log.warn("[MQ] 发送失败回执 taskId={}, message={}",
                callback.taskId(), callback.message());
    }

    /**
     * 获取所有已发送的回执（用于测试/查询）。
     */
    public List<TaggingCallback> getSentCallbacks() {
        return List.copyOf(sentCallbacks);
    }

    /**
     * 清空回执记录（用于测试）。
     */
    public void clear() {
        sentCallbacks.clear();
    }
}
