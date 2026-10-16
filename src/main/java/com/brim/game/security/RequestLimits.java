package com.brim.game.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded token buckets; no task is allocated for each incoming command. */
@Component
public class RequestLimits {
    private final Map<String, Bucket> buckets = new HashMap<>();
    public synchronized boolean allow(String key, int capacity, double perSecond) {
        long now = System.nanoTime();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= 8192) return false;
            bucket = new Bucket(capacity, now);
            buckets.put(key, bucket);
        }
        bucket.tokens = Math.min(capacity, bucket.tokens + (now - bucket.at) / 1e9 * perSecond);
        bucket.at = now;
        if (bucket.tokens < 1) return false;
        bucket.tokens--;
        return true;
    }
    @Scheduled(fixedDelay = 60000)
    public synchronized void cleanup() {
        long cutoff = System.nanoTime() - 600_000_000_000L;
        buckets.values().removeIf(bucket -> bucket.at < cutoff);
    }
    private static class Bucket {
        double tokens;
        long at;
        Bucket(double tokens, long at) { this.tokens = tokens; this.at = at; }
    }
}
