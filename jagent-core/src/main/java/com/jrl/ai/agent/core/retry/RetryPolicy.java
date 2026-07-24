package com.jrl.ai.agent.core.retry;

import java.time.Duration;

/**
 * 重试策略
 */
public interface RetryPolicy {

    /**
     * 是否应重试
     */
    boolean shouldRetry(int attempt, Throwable error);

    /**
     * 获取重试延迟
     */
    Duration getDelay(int attempt);

    /**
     * 最大重试次数
     */
    int maxAttempts();

    /**
     * 创建固定次数重试策略
     */
    static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new FixedRetryPolicy(maxAttempts, delay);
    }

    /**
     * 创建指数退避重试策略
     */
    static RetryPolicy exponential(int maxAttempts, Duration initialDelay) {
        return new ExponentialRetryPolicy(maxAttempts, initialDelay);
    }
}
