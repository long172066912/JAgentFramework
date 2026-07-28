package com.jrl.ai.agent.demo.tagging.mq;

import com.jrl.ai.agent.demo.tagging.model.CallbackType;
import com.jrl.ai.agent.demo.tagging.model.TaggingCallback;
import com.jrl.ai.agent.demo.tagging.model.TaggingTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 回执分发器 — 根据任务配置将回执发送到 MQ 或 HTTP。
 *
 * <p>对应 AI Agent 交互协议中的「任务处理回执」。
 * 支持两种回执方式：
 * <ul>
 *   <li>MQ：发送到消息队列（默认）</li>
 *   <li>HTTP：POST 请求到指定 URL</li>
 * </ul>
 */
@Component
public class CallbackDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CallbackDispatcher.class);

    private final CallbackProducer mqProducer;
    private final RestTemplate restTemplate;

    public CallbackDispatcher(CallbackProducer mqProducer) {
        this.mqProducer = mqProducer;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 分发回执 — 根据任务的 callbackType 选择发送方式。
     *
     * @param task     原任务
     * @param callback 回执内容
     */
    public void dispatch(TaggingTask task, TaggingCallback callback) {
        CallbackType callbackType = task.callbackType();

        if (callbackType == CallbackType.HTTP) {
            sendHttp(task, callback);
        } else {
            sendMq(task, callback);
        }
    }

    /**
     * 发送成功回执。
     *
     * @param task     原任务
     * @param callback 回执内容
     */
    public void sendSuccess(TaggingTask task, TaggingCallback callback) {
        dispatch(task, callback);
    }

    /**
     * 发送失败回执。
     *
     * @param task     原任务
     * @param callback 回执内容
     */
    public void sendFail(TaggingTask task, TaggingCallback callback) {
        dispatch(task, callback);
    }

    /**
     * 通过 MQ 发送回执。
     */
    private void sendMq(TaggingTask task, TaggingCallback callback) {
        if (TaggingCallback.STATUS_SUCCESS.equals(callback.status())) {
            mqProducer.sendSuccess(callback);
        } else {
            mqProducer.sendFail(callback);
        }
        log.info("[Callback] MQ 回执已发送 taskId={}, address={}",
                task.taskId(), task.callbackAddress());
    }

    /**
     * 通过 HTTP POST 发送回执。
     */
    private void sendHttp(TaggingTask task, TaggingCallback callback) {
        String url = task.callbackAddress();
        if (url == null || url.isBlank()) {
            log.warn("[Callback] HTTP 回执未配置地址，回退到 MQ taskId={}", task.taskId());
            sendMq(task, callback);
            return;
        }

        try {
            restTemplate.postForEntity(url, callback, String.class);
            log.info("[Callback] HTTP 回执已发送 taskId={}, url={}", task.taskId(), url);
        } catch (Exception e) {
            log.error("[Callback] HTTP 回执发送失败 taskId={}, url={}", task.taskId(), url, e);
            // 失败时回退到 MQ
            sendMq(task, callback);
        }
    }
}
