package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.PathHealthResponseDto;
import com.netpath.repository.NetworkPathRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Redis adapter for path health, and nothing else.
 *
 * <p>It owns one key per path ({@code path:health:{id}}) and the read-through policy: serve the
 * cached classification, or rebuild it through {@link PathHealthService} and cache it. It never
 * writes domain state — persisting a status belongs to {@link PathHealthService}.
 *
 * <p>Redis is an accelerator, never a source of truth, so an unavailable Redis degrades to
 * recomputation instead of failing. Repeated failures are logged once per
 * {@link #FAILURE_LOG_INTERVAL_MS} window with a suppressed count, so an outage cannot flood the log.
 */
@Service
public class PathHealthCacheService {

    private static final Logger logger = LoggerFactory.getLogger(PathHealthCacheService.class);
    private static final String CACHE_KEY_PREFIX = "path:health:";
    private static final long FAILURE_LOG_INTERVAL_MS = 30_000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final PathHealthService pathHealthService;
    private final NetworkPathRepository networkPathRepository;
    private final Duration ttl;

    private final AtomicLong suppressedFailures = new AtomicLong();
    private volatile long lastFailureLoggedAt = 0L;

    public PathHealthCacheService(RedisTemplate<String, Object> redisTemplate,
                                  PathHealthService pathHealthService,
                                  NetworkPathRepository networkPathRepository,
                                  PathHealthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.pathHealthService = pathHealthService;
        this.networkPathRepository = networkPathRepository;
        this.ttl = Duration.ofSeconds(properties.getCache().getTtlSeconds());
    }

    /** Current health for a path, from Redis when possible and recomputed when not. */
    public PathHealthResponseDto get(Long pathId) {
        String cacheKey = CACHE_KEY_PREFIX + pathId;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof PathHealthResponseDto health) {
                logger.debug("Cache hit for path {}", pathId);
                return health;
            }
            logger.debug("Cache miss for path {}, rebuilding from PostgreSQL", pathId);
        } catch (RuntimeException e) {
            logFailure("Redis read failed for path " + pathId, e);
            return recompute(pathId);
        }

        PathHealthResponseDto health = recompute(pathId);
        if (health != null) {
            put(pathId, health);
        }
        return health;
    }

    public void put(Long pathId, PathHealthResponseDto health) {
        if (health == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + pathId, health, ttl);
            logger.debug("Cached health for path {} with TTL {}s", pathId, ttl.toSeconds());
        } catch (RuntimeException e) {
            logFailure("Failed to cache health for path " + pathId, e);
        }
    }

    public void invalidate(Long pathId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + pathId);
        } catch (RuntimeException e) {
            logFailure("Failed to invalidate cache for path " + pathId, e);
        }
    }

    private PathHealthResponseDto recompute(Long pathId) {
        return networkPathRepository.findById(pathId)
                .map(pathHealthService::evaluate)
                .orElse(null);
    }

    private void logFailure(String message, RuntimeException cause) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLoggedAt < FAILURE_LOG_INTERVAL_MS) {
            suppressedFailures.incrementAndGet();
            return;
        }

        lastFailureLoggedAt = now;
        long suppressed = suppressedFailures.getAndSet(0);
        logger.warn("{}{}. Serving from PostgreSQL; hot state is not shared between instances.",
                message,
                suppressed > 0 ? " (" + suppressed + " similar failures suppressed)" : "");
        logger.debug("Redis failure detail", cause);
    }
}
