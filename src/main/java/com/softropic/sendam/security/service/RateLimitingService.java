package com.softropic.sendam.security.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing rate limit buckets using Bucket4j.
 */
@Service
public class RateLimitingService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Attempts to consume a token from the bucket associated with the given identifier.
     *
     * @param identifier unique identifier for the client (e.g., IP address)
     * @param limitKey   unique key for the rate limit type (e.g., "registration")
     * @param capacity   maximum tokens in the bucket
     * @param duration   duration for tokens refill
     * @param unit       time unit for duration
     * @return true if a token was consumed, false if rate limit was exceeded
     */
    public boolean tryConsume(String identifier, String limitKey, long capacity, long duration, TimeUnit unit) {
        String bucketKey = limitKey + ":" + identifier;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(capacity, duration, unit));
        return bucket.tryConsume(1);
    }

    /**
     * Attempts to consume N tokens from the bucket. Infrastructure for per-unit limits
     * (e.g., recipient count for SMS sends in Phase 3).
     * NOTE: This method provides the bucket mechanics only. The 1000 recipients/min
     * enforcement (AUTH-05 second half) is wired in Phase 3's SMS send endpoint
     * where recipient count is available.
     */
    public boolean tryConsume(String identifier, String limitKey, long capacity,
                              long duration, TimeUnit unit, long tokensToConsume) {
        String bucketKey = limitKey + ":" + identifier;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(capacity, duration, unit));
        return bucket.tryConsume(tokensToConsume);
    }

    private Bucket createBucket(long capacity, long duration, TimeUnit unit) {
        Duration window = Duration.of(duration, unit.toChronoUnit());
        Refill refill = window.toSeconds() < 60
            ? Refill.greedy(capacity, window)
            : Refill.intervally(capacity, window);
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
