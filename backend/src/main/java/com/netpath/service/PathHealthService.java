package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.PathHealthResponseDto;
import com.netpath.dto.PathTelemetrySummary;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The health authority: it owns the classification rule and is the only writer of
 * {@code network_paths.status}.
 *
 * <p>It knows nothing about Redis or HTTP. Callers that need cached reads go through
 * {@link PathHealthCacheService}; callers that need the stored projection call
 * {@link #evaluateAndPersistStatus(NetworkPath)} after telemetry arrives.
 */
@Service
public class PathHealthService {

    private static final Logger logger = LoggerFactory.getLogger(PathHealthService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final PathMetricRepository pathMetricRepository;
    private final NetworkPathRepository networkPathRepository;
    private final PathHealthProperties properties;

    public PathHealthService(PathMetricRepository pathMetricRepository,
                             NetworkPathRepository networkPathRepository,
                             PathHealthProperties properties) {
        this.pathMetricRepository = pathMetricRepository;
        this.networkPathRepository = networkPathRepository;
        this.properties = properties;
    }

    /**
     * Classifies a path from its telemetry summary. Pure: no database access, no clock.
     *
     * <p>A path is {@code DOWN} when its worst sample has crossed the severe boundary. Below that it
     * is {@code DEGRADED} as soon as an average leaves the healthy band, and {@code HEALTHY} inside
     * it. Too few samples to be meaningful is {@code UNKNOWN}.
     */
    public PathStatus classify(PathTelemetrySummary summary) {
        PathHealthProperties.Thresholds thresholds = properties.getThresholds();

        if (!summary.hasEnoughSamples(thresholds.getMinMetricsForEvaluation())) {
            return PathStatus.UNKNOWN;
        }

        if (atLeast(summary.maxLatencyMs(), thresholds.getDownLatencyMs())
                || atLeast(summary.maxPacketLossPct(), thresholds.getDownPacketLossPct())) {
            return PathStatus.DOWN;
        }

        if (atLeast(summary.averageLatencyMs(), thresholds.getDegradedLatencyMs())
                || atLeast(summary.averagePacketLossPct(), thresholds.getDegradedPacketLossPct())) {
            return PathStatus.DEGRADED;
        }

        return PathStatus.HEALTHY;
    }

    private boolean atLeast(Double value, double boundary) {
        return value != null && value >= boundary;
    }

    @Transactional(readOnly = true)
    public PathTelemetrySummary summarise(Long pathId) {
        if (pathId == null) {
            return null;
        }
        return summarise(List.of(pathId))
                .getOrDefault(pathId, PathTelemetrySummary.empty(pathId));
    }

    /** Batch form used when rendering a page of paths. */
    @Transactional(readOnly = true)
    public Map<Long, PathTelemetrySummary> summarise(Collection<Long> pathIds) {
        Map<Long, PathTelemetrySummary> summaries = new HashMap<>();
        if (pathIds == null || pathIds.isEmpty()) {
            return summaries;
        }

        for (Object[] row : pathMetricRepository.summariseForPaths(pathIds)) {
            PathTelemetrySummary summary = PathTelemetrySummary.fromRow(row);
            summaries.put(summary.pathId(), summary);
        }
        return summaries;
    }

    /** Read-only evaluation: used by the cache on a miss and by the recommendation engine. */
    public PathHealthResponseDto evaluate(NetworkPath path) {
        if (path == null || path.getId() == null) {
            return null;
        }
        return toResponse(path, summarise(path.getId()));
    }

    /**
     * Evaluates and stores the result as the path's current status. Called after new telemetry, and
     * when the demo estate is seeded.
     */
    @Transactional
    public PathHealthResponseDto evaluateAndPersistStatus(NetworkPath path) {
        PathHealthResponseDto health = evaluate(path);
        if (health == null) {
            return null;
        }

        path.setStatus(PathStatus.valueOf(health.getStatus()));
        networkPathRepository.save(path);

        logger.debug("Path {} status is {}", path.getId(), health.getStatus());
        return health;
    }

    private PathHealthResponseDto toResponse(NetworkPath path, PathTelemetrySummary summary) {
        // The response is stamped with the newest sample, not with the time of the request, so
        // clients can tell how fresh the underlying telemetry is.
        String lastUpdated = summary.lastTimestamp() != null
                ? DATE_FORMATTER.format(summary.lastTimestamp())
                : DATE_FORMATTER.format(Instant.now());

        return new PathHealthResponseDto(
                path.getId(),
                classify(summary).name(),
                summary.averageLatencyMs(),
                summary.averagePacketLossPct(),
                summary.maxLatencyMs(),
                summary.maxPacketLossPct(),
                summary.sampleCount(),
                lastUpdated);
    }
}
