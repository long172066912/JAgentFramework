package com.jrl.ai.agent.core.retry;

import java.time.Duration;

record ExponentialRetryPolicy(int maxAttempts, Duration initialDelay) implements RetryPolicy {

    @Override
    public boolean shouldRetry(int attempt, Throwable error) {
        return attempt < maxAttempts;
    }

    @Override
    public Duration getDelay(int attempt) {
        return initialDelay.multipliedBy((long) Math.pow(2, attempt - 1));
    }
}
