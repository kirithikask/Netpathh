package com.netpath.service;

import com.netpath.dto.PathHealthResponseDto;
import com.netpath.dto.RouteRecommendationDto;
import com.netpath.entity.Endpoint;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import com.netpath.entity.RouteRecommendation;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.RouteRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Ranks the alternative paths configured between the same two endpoints and recommends one only when
 * it is measurably better than the path in use. The ranking rules are documented in
 * docs/route-recommendation.md.
 */
@Service
public class RouteRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RouteRecommendationService.class);

    private final NetworkPathRepository networkPathRepository;
    private final RouteRecommendationRepository recommendationRepository;
    private final PathHealthService pathHealthService;

    public RouteRecommendationService(NetworkPathRepository networkPathRepository,
                                      RouteRecommendationRepository recommendationRepository,
                                      PathHealthService pathHealthService) {
        this.networkPathRepository = networkPathRepository;
        this.recommendationRepository = recommendationRepository;
        this.pathHealthService = pathHealthService;
    }

    /** Returns {@code null} when the path does not exist; the controller maps that to 404. */
    @Transactional(readOnly = true)
    public RouteRecommendationDto generateRecommendation(Long pathId) {
        Optional<NetworkPath> currentPathOpt = networkPathRepository.findByIdWithDetails(pathId);
        if (currentPathOpt.isEmpty()) {
            logger.warn("Cannot generate recommendation: path {} not found", pathId);
            return null;
        }

        NetworkPath currentPath = currentPathOpt.get();
        Endpoint source = currentPath.getSourceEndpoint();
        Endpoint destination = currentPath.getDestinationEndpoint();

        PathHealthResponseDto currentHealth = pathHealthService.evaluate(currentPath);

        List<NetworkPath> alternatives = networkPathRepository.findAlternativePaths(
                source.getId(), destination.getId(), pathId);

        NetworkPath bestAlternative = null;
        PathHealthResponseDto bestHealth = null;

        for (NetworkPath alternative : alternatives) {
            PathHealthResponseDto alternativeHealth = pathHealthService.evaluate(alternative);
            if (alternativeHealth == null) {
                continue;
            }
            if (bestHealth == null || isHealthier(alternativeHealth, bestHealth)) {
                bestAlternative = alternative;
                bestHealth = alternativeHealth;
            }
        }

        boolean worthRecommending = bestAlternative != null
                && currentHealth != null
                && isHealthier(bestHealth, currentHealth);

        RouteRecommendationDto recommendation = buildRecommendation(
                currentPath, source, destination, currentHealth);

        if (worthRecommending) {
            recommendation.setRecommendedPathId(bestAlternative.getId());
            recommendation.setRecommendedPathName(bestAlternative.getPathName());
            recommendation.setRecommendedStatus(bestHealth.getStatus());
            recommendation.setRecommendedAvgLatencyMs(bestHealth.getAverageLatencyMs());
            recommendation.setRecommendedAvgPacketLossPct(bestHealth.getAveragePacketLossPct());
            recommendation.setReason(describeImprovement(currentHealth, bestHealth));
            recommendation.setHasAlternative(true);

            persistRecommendation(currentPath, bestAlternative, source, destination, recommendation);
        } else {
            recommendation.setReason(alternatives.isEmpty()
                    ? "No alternative paths are configured between these endpoints"
                    : "No alternative path is currently healthier than the current path");
            recommendation.setHasAlternative(false);
        }

        recommendation.setRecommendationTimestamp(Instant.now().toString());
        return recommendation;
    }

    private RouteRecommendationDto buildRecommendation(NetworkPath currentPath,
                                                       Endpoint source,
                                                       Endpoint destination,
                                                       PathHealthResponseDto currentHealth) {
        RouteRecommendationDto recommendation = new RouteRecommendationDto();
        recommendation.setCurrentPathId(currentPath.getId());
        recommendation.setCurrentPathName(currentPath.getPathName());
        recommendation.setCurrentStatus(currentHealth != null
                ? currentHealth.getStatus()
                : PathStatus.UNKNOWN.name());
        recommendation.setCurrentAvgLatencyMs(currentHealth != null
                ? currentHealth.getAverageLatencyMs() : null);
        recommendation.setCurrentAvgPacketLossPct(currentHealth != null
                ? currentHealth.getAveragePacketLossPct() : null);
        recommendation.setSourceEndpoint(label(source));
        recommendation.setDestinationEndpoint(label(destination));
        return recommendation;
    }

    private static String label(Endpoint endpoint) {
        return endpoint.getName() + " (" + endpoint.getIpAddress() + ")";
    }

    /**
     * Status first, then average latency, then packet loss. UNKNOWN ranks last: no data is not
     * evidence of a better path.
     */
    private boolean isHealthier(PathHealthResponseDto candidate, PathHealthResponseDto current) {
        if (candidate == null || current == null) {
            return false;
        }

        PathStatus candidateStatus = PathStatus.valueOf(candidate.getStatus());
        PathStatus currentStatus = PathStatus.valueOf(current.getStatus());

        if (statusRank(candidateStatus) != statusRank(currentStatus)) {
            return statusRank(candidateStatus) < statusRank(currentStatus);
        }

        return lower(candidate.getAverageLatencyMs(), current.getAverageLatencyMs())
                || (equal(candidate.getAverageLatencyMs(), current.getAverageLatencyMs())
                    && lower(candidate.getAveragePacketLossPct(), current.getAveragePacketLossPct()));
    }

    private int statusRank(PathStatus status) {
        return switch (status) {
            case HEALTHY -> 0;
            case DEGRADED -> 1;
            case DOWN -> 2;
            case UNKNOWN -> 3;
        };
    }

    private boolean lower(Double candidate, Double current) {
        return candidate != null && current != null && candidate < current;
    }

    private boolean equal(Double a, Double b) {
        return a != null && a.equals(b);
    }

    private String describeImprovement(PathHealthResponseDto current, PathHealthResponseDto alternative) {
        StringBuilder reason = new StringBuilder();

        if (current.getStatus() != null && !current.getStatus().equals(alternative.getStatus())) {
            reason.append("Status improvement: ")
                  .append(current.getStatus())
                  .append(" → ")
                  .append(alternative.getStatus());
        }

        Double latencyGain = difference(current.getAverageLatencyMs(), alternative.getAverageLatencyMs());
        if (latencyGain != null && latencyGain > 0) {
            if (reason.length() > 0) {
                reason.append(". ");
            }
            reason.append(String.format("Latency %.0fms lower (%.0fms vs %.0fms)",
                    latencyGain, alternative.getAverageLatencyMs(), current.getAverageLatencyMs()));
        }

        Double lossGain = difference(current.getAveragePacketLossPct(), alternative.getAveragePacketLossPct());
        if (lossGain != null && lossGain > 0) {
            if (reason.length() > 0) {
                reason.append(". ");
            }
            reason.append(String.format("Packet loss %.2f%% lower (%.2f%% vs %.2f%%)",
                    lossGain, alternative.getAveragePacketLossPct(), current.getAveragePacketLossPct()));
        }

        return reason.length() == 0
                ? "Alternative path has comparable metrics"
                : reason.toString();
    }

    private Double difference(Double current, Double alternative) {
        return current != null && alternative != null ? current - alternative : null;
    }

    private void persistRecommendation(NetworkPath currentPath, NetworkPath recommendedPath,
                                       Endpoint source, Endpoint destination,
                                       RouteRecommendationDto recommendation) {
        try {
            recommendationRepository.save(new RouteRecommendation(
                    currentPath,
                    recommendedPath,
                    recommendation.getReason(),
                    source.getId(),
                    destination.getId(),
                    PathStatus.valueOf(recommendation.getRecommendedStatus())));
        } catch (RuntimeException e) {
            // The audit trail is useful but must never fail an operator's read request.
            logger.error("Failed to persist recommendation for path {}: {}",
                    currentPath.getId(), e.getMessage());
        }
    }
}
