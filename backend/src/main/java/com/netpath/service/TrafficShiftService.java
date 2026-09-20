package com.netpath.service;

import com.netpath.dto.TrafficShiftRequest;
import com.netpath.dto.TrafficShiftResponse;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.ShiftStatus;
import com.netpath.entity.TrafficShiftLog;
import com.netpath.exception.BadRequestException;
import com.netpath.exception.ResourceNotFoundException;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.TrafficShiftLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Records what a traffic shift between two paths <em>would</em> do.
 *
 * <p>NETPATH does not talk to routers, so nothing is actually rerouted. Every call is persisted as
 * a {@link ShiftStatus#SIMULATED} record in {@code traffic_shift_logs}; the audit trail is the
 * feature, and the API responses are worded so no one can mistake this for production control.
 */
@Service
public class TrafficShiftService {

    private static final Logger logger = LoggerFactory.getLogger(TrafficShiftService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final int MAX_RECENT_SHIFTS = 50;

    private final NetworkPathRepository networkPathRepository;
    private final TrafficShiftLogRepository trafficShiftLogRepository;

    public TrafficShiftService(NetworkPathRepository networkPathRepository,
                               TrafficShiftLogRepository trafficShiftLogRepository) {
        this.networkPathRepository = networkPathRepository;
        this.trafficShiftLogRepository = trafficShiftLogRepository;
    }

    @Transactional
    public TrafficShiftResponse simulateShift(Long pathId, TrafficShiftRequest request) {
        NetworkPath currentPath = networkPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("NetworkPath not found with id: " + pathId));

        if (request.getRecommendedPathId() == null) {
            throw new BadRequestException("recommendedPathId is required to simulate a shift");
        }

        NetworkPath targetPath = networkPathRepository.findById(request.getRecommendedPathId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target NetworkPath not found with id: " + request.getRecommendedPathId()));

        if (targetPath.getId().equals(currentPath.getId())) {
            throw new BadRequestException("A path cannot be shifted to itself");
        }

        Long currentSource = currentPath.getSourceEndpoint() != null ? currentPath.getSourceEndpoint().getId() : null;
        Long currentDestination = currentPath.getDestinationEndpoint() != null
                ? currentPath.getDestinationEndpoint().getId() : null;

        if (!currentSource.equals(targetPath.getSourceEndpoint().getId())
                || !currentDestination.equals(targetPath.getDestinationEndpoint().getId())) {
            throw new BadRequestException(
                    "Target path must connect the same source and destination endpoints as the current path");
        }

        TrafficShiftLog log = new TrafficShiftLog(
                currentPath, currentPath, targetPath, request.getReason(), ShiftStatus.SIMULATED);
        TrafficShiftLog saved = trafficShiftLogRepository.save(log);

        logger.info("Simulated traffic shift for path {} -> path {} (log id={})",
                currentPath.getId(), targetPath.getId(), saved.getId());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TrafficShiftResponse> getShiftHistory(Long pathId) {
        if (!networkPathRepository.existsById(pathId)) {
            throw new ResourceNotFoundException("NetworkPath not found with id: " + pathId);
        }
        return trafficShiftLogRepository.findByPathIdOrderByShiftedAtDesc(pathId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TrafficShiftResponse> getRecentShifts(int limit) {
        if (limit < 1 || limit > MAX_RECENT_SHIFTS) {
            throw new BadRequestException("'limit' must be between 1 and " + MAX_RECENT_SHIFTS);
        }

        return trafficShiftLogRepository.findAllByOrderByShiftedAtDesc().stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private TrafficShiftResponse toDto(TrafficShiftLog log) {
        return new TrafficShiftResponse(
                log.getId(),
                log.getPath() != null ? log.getPath().getId() : null,
                log.getOldPath() != null ? log.getOldPath().getId() : null,
                log.getOldPath() != null ? log.getOldPath().getPathName() : null,
                log.getNewPath() != null ? log.getNewPath().getId() : null,
                log.getNewPath() != null ? log.getNewPath().getPathName() : null,
                log.getReason(),
                log.getStatus() != null ? log.getStatus().name() : null,
                log.getShiftedAt() != null ? DATE_FORMATTER.format(log.getShiftedAt()) : null
        );
    }
}
