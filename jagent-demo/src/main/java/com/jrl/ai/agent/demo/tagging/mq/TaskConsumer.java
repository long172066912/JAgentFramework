package com.jrl.ai.agent.demo.tagging.mq;

import com.jrl.ai.agent.demo.tagging.model.CallbackType;
import com.jrl.ai.agent.demo.tagging.model.TaggingCallback;
import com.jrl.ai.agent.demo.tagging.model.TaggingResult;
import com.jrl.ai.agent.demo.tagging.model.TaggingTask;
import com.jrl.ai.agent.demo.tagging.service.TaggingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MQ 任务消费者 — 接收打标任务并处理。
 *
 * <p>对应 AI Agent 交互协议中的「处理任务投递协议」。
 * 生产环境替换为真实 Kafka/RocketMQ 消费者。
 *
 * <p>流程：
 * <ol>
 *   <li>从 MQ 接收 TaggingTask</li>
 *   <li>调用 TaggingService 执行打标</li>
 *   <li>通过 CallbackProducer 发送回执</li>
 * </ol>
 */
@Component
public class TaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumer.class);

    private final TaggingService taggingService;
    private final CallbackDispatcher callbackDispatcher;

    public TaskConsumer(TaggingService taggingService, CallbackDispatcher callbackDispatcher) {
        this.taggingService = taggingService;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * 消费打标任务（模拟 MQ 消费）。
     *
     * @param task 打标任务
     */
    public void consume(TaggingTask task) {
        long startTime = System.currentTimeMillis();
        log.info("[MQ] 收到打标任务 taskId={}, type={}, priority={}",
                task.taskId(), task.taskType(), task.priority());

        try {
            // 提取内容信息
            String contentId = extractContentId(task);
            String contentType = task.payloadType();
            String contentText = extractContentText(task);
            int requiredTagCount = task.requiredTagCount() > 0 ? task.requiredTagCount() : 5;

            // 执行打标
            TaggingResult result = taggingService.tag(contentId, contentType, contentText, requiredTagCount);

            // 构建成功回执
            Map<String, Object> payload = Map.of(
                    "tags", result.tags().stream()
                            .map(t -> Map.of(
                                    "id", t.id(),
                                    "name", t.tagName(),
                                    "category", t.category(),
                                    "confidence", t.confidence()
                            ))
                            .toList(),
                    "tagCount", result.tags().size()
            );

            TaggingCallback callback = TaggingCallback.success(task, payload, result.processTime());
            if (task.callbackType() != CallbackType.NONE) {
                callbackDispatcher.sendSuccess(task, callback);
            } else {
                log.info("[MQ] 任务完成，不回执 taskId={}", task.taskId());
            }

        } catch (Exception e) {
            log.error("[MQ] 打标任务失败 taskId={}", task.taskId(), e);

            TaggingCallback callback = TaggingCallback.fail(task, e.getMessage(),
                    System.currentTimeMillis() - startTime);
            if (task.callbackType() != CallbackType.NONE) {
                callbackDispatcher.sendFail(task, callback);
            } else {
                log.info("[MQ] 任务失败，不回执 taskId={}", task.taskId());
            }
        }
    }

    /**
     * 从任务中提取内容 ID。
     */
    private String extractContentId(TaggingTask task) {
        Object id = task.payload().get("contentId");
        if (id == null) {
            id = task.payload().get("id");
        }
        return id != null ? id.toString() : "unknown_" + task.taskId();
    }

    /**
     * 从任务中提取内容文本。
     */
    private String extractContentText(TaggingTask task) {
        StringBuilder sb = new StringBuilder();

        // 标题
        Object title = task.payload().get("title");
        if (title != null) {
            sb.append("标题：").append(title).append("\n");
        }

        // 描述
        Object description = task.payload().get("description");
        if (description != null) {
            sb.append("描述：").append(description).append("\n");
        }

        // 图片描述
        Object images = task.payload().get("images");
        if (images != null) {
            sb.append("图片描述：").append(images).append("\n");
        }

        // 备注
        if (task.remark() != null && !task.remark().isBlank()) {
            sb.append("备注：").append(task.remark()).append("\n");
        }

        return sb.length() > 0 ? sb.toString() : "无内容描述";
    }
}
