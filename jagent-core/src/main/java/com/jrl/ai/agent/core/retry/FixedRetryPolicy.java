package com.jrl.ai.agent.core.retry;

import java.time.Duration;

record FixedRetryPolicy(int maxAttempts, Duration delay) implements RetryPolicy {

    @Override
    public boolean shouldRetry(int attempt, Throwable error) {
        return attempt < maxAttempts;
    }

    @Override
    public Duration getDelay(int attempt) {
        return delay;
    }
}
