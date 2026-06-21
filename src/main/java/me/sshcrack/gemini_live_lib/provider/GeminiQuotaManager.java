package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.mc_talking.api.session.QuotaManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GeminiQuotaManager implements QuotaManager {
    private final int maxConcurrent;
    private final Map<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    public GeminiQuotaManager() {
        this(3);
    }

    public GeminiQuotaManager(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    @Override
    public boolean tryConsume(String resourceKey) {
        AtomicInteger counter = activeCounts.computeIfAbsent(resourceKey, k -> new AtomicInteger(0));
        int current = counter.get();
        if (current >= maxConcurrent) {
            return false;
        }
        return counter.incrementAndGet() <= maxConcurrent;
    }

    @Override
    public void reportSuccess() {
        // No-op: success doesn't affect quota tracking for Gemini
    }

    @Override
    public void reportFailure(Throwable error) {
        // On failure, decrement the counter for all keys
        activeCounts.values().forEach(c -> {
            int val = c.get();
            if (val > 0) {
                c.decrementAndGet();
            }
        });
    }

    public void release(String resourceKey) {
        AtomicInteger counter = activeCounts.get(resourceKey);
        if (counter != null) {
            int val = counter.get();
            if (val > 0) {
                counter.decrementAndGet();
            }
        }
    }

    public int getActiveCount() {
        return activeCounts.values().stream().mapToInt(AtomicInteger::get).sum();
    }
}
