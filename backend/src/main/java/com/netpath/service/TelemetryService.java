package com.netpath.service;

import com.netpath.dto.PathHealthResponseDto;
import com.netpath.dto.TelemetryRequest;
import com.netpath.dto.TelemetryResponseDto;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathMetric;
import com.netpath.exception.BadRequestException;
import com.netpath.exception.ResourceNotFoundException;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns telemetry: writing samples in, reading history out.
 *
 * <p>Ingestion is the one flow that changes a path's health, so it is the one place that sequences
 * three owners: persist the sample, ask {@link PathHealthService} to classify and store the status,
 * then hand the result to {@link PathHealthCacheService} for the hot state. Both the sample and the
 * status land in the same transaction.
 */
@Service
public class TelemetryService {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final int MAX_RECENT_SAMPLES = 100;

    private final PathMetricRepository pathMetricRepository;
    private final NetworkPathRepository networkPathRepository;
    private final PathHealthService pathHealthService;
    private final PathHealthCacheService pathHealthCacheService;

    public TelemetryService(PathMetricRepository pathMetricRepository,
                            NetworkPathRepository networkPathRepository,
                            PathHealthService pathHealthService,
                            PathHealthCacheService pathHealthCacheService) {
        this.pathMetricRepository = pathMetricRepository;
        this.networkPathRepository = networkPathRepository;
        this.pathHealthService = pathHealthService;
        this.pathHealthCacheService = pathHealthCacheService;
    }

    @Transactional
    public PathHealthResponseDto ingest(Long pathId, TelemetryRequest request) {
        NetworkPath path = requirePath(pathId);

        pathMetricRepository.save(new PathMetric(
                path,
                request.getLatencyMs(),
                request.getPacketLossPct(),
                request.getThroughputMbps()));

        PathHealthResponseDto health = pathHealthService.evaluateAndPersistStatus(path);
        pathHealthCacheService.put(pathId, health);

        logger.debug("Ingested telemetry for path {}: latency={}ms packetLoss={}% -> {}",
                pathId, request.getLatencyMs(), request.getPacketLossPct(), health.getStatus());

        return health;
    }

    @Transactional(readOnly = true)
    public Page<TelemetryResponseDto> history(Long pathId, Instant from, Instant to, Pageable pageable) {
        requirePath(pathId);

        if (from == null && to == null) {
            return pathMetricRepository.findByPathIdOrderByTimestampDesc(pathId, pageable)
                    .map(this::toDto);
        }

        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must be earlier than 'to'");
        }

        return pathMetricRepository.findByPathIdAndTimestampBetween(pathId, start, end, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<TelemetryResponseDto> recent(Long pathId, int limit) {
        requirePath(pathId);

        if (limit < 1 || limit > MAX_RECENT_SAMPLES) {
            throw new BadRequestException("'limit' must be between 1 and " + MAX_RECENT_SAMPLES);
        }

        return pathMetricRepository.findRecentByPathId(pathId, PageRequest.of(0, limit)).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private NetworkPath requirePath(Long pathId) {
        return networkPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("NetworkPath not found with id: " + pathId));
    }

    private TelemetryResponseDto toDto(PathMetric metric) {
        return new TelemetryResponseDto(
                metric.getId(),
                metric.getPath() != null ? metric.getPath().getId() : null,
                metric.getLatencyMs(),
                metric.getPacketLossPct(),
                metric.getThroughputMbps(),
                metric.getTimestamp() != null ? DATE_FORMATTER.format(metric.getTimestamp()) : null);
    }
}
