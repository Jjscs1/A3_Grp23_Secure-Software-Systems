package com.evp.identity.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/** Single-process sliding windows. No user records, passwords, tokens or ballot bodies. */
@Component
public class RequestRateLimiter {
    public record Budget(String key, int limit) {
        public Budget {
            if (key == null || limit < 1) throw new IllegalArgumentException("Invalid budget");
        }
    }
    private final Map<String, ArrayDeque<Long>> windows = new HashMap<>();
    private final LongSupplier millis;
    private final long windowMillis;
    private final int capacity;
    private long lastSweep;

    public RequestRateLimiter() {
        this(() -> System.nanoTime() / 1_000_000, Duration.ofMinutes(1), 10000);
    }
    RequestRateLimiter(LongSupplier millis, Duration window, int capacity) {
        this.millis = millis;
        this.windowMillis = window.toMillis();
        this.capacity = capacity;
        this.lastSweep = millis.getAsLong();
        if (windowMillis < 1 || capacity < 1) throw new IllegalArgumentException("Invalid capacity/window");
    }

    /** Zero permits; positive value is Retry-After seconds. Rejections never extend a window. */
    public synchronized long acquire(List<Budget> budgets) {
        long now = millis.getAsLong();
        if (now - lastSweep >= 1000 || windows.size() >= capacity) {
            windows.values().forEach(q -> expire(q, now));
            windows.values().removeIf(ArrayDeque::isEmpty);
            lastSweep = now;
        }
        long waitMillis = 0;
        long newKeys = 0;
        for (Budget budget : budgets) {
            ArrayDeque<Long> q = windows.get(budget.key());
            if (q == null) { newKeys++; continue; }
            expire(q, now);
            if (q.size() >= budget.limit()) {
                waitMillis = Math.max(waitMillis, q.getFirst() + windowMillis - now);
            }
        }
        if (waitMillis > 0) return seconds(waitMillis);
        // Do not evict live buckets: eviction would let rotating clients reset other limits.
        if (windows.size() + newKeys > capacity) return seconds(windowMillis);
        for (Budget budget : budgets) {
            windows.computeIfAbsent(budget.key(), k -> new ArrayDeque<>()).addLast(now);
        }
        return 0;
    }
    private void expire(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.getFirst() >= windowMillis) q.removeFirst();
    }
    private long seconds(long milliseconds) { return Math.max(1, (milliseconds + 999) / 1000); }
}
