package com.simplemdm.service.integration;

import java.time.Duration;

/** A monotonic deadline shared by every blocking phase of one delivery attempt. */
public final class AttemptDeadline {
    private final long deadlineNanos;

    private AttemptDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public static AttemptDeadline after(Duration timeout) {
        long timeoutNanos = Math.max(0, timeout.toNanos());
        long now = System.nanoTime();
        long deadline = now + timeoutNanos;
        if (timeoutNanos > 0 && deadline < now) deadline = Long.MAX_VALUE;
        return new AttemptDeadline(deadline);
    }

    public long remainingNanos() {
        return Math.max(0, deadlineNanos - System.nanoTime());
    }

    public Duration remainingDuration() {
        return Duration.ofNanos(remainingNanos());
    }

    public boolean isExpired() {
        return remainingNanos() == 0;
    }
}
