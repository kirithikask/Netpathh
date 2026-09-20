package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.RouteRecommendationDto;
import com.netpath.entity.Endpoint;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import com.netpath.entity.RouteRecommendation;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import com.netpath.repository.RouteRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteRecommendationServiceTest {

    private static final long CURRENT_PATH_ID = 1L;
    private static final long HEALTHY_ALT_ID = 2L;
    private static final long WORSE_ALT_ID = 3L;

    private NetworkPathRepository networkPathRepository;
    private RouteRecommendationRepository recommendationRepository;
    private RouteRecommendationService service;

    /** Telemetry rows keyed by path, served through the single aggregate query. */
    private final Map<Long, Object[]> telemetry = new HashMap<>();

    @BeforeEach
    void setUp() {
        networkPathRepository = mock(NetworkPathRepository.class);
        recommendationRepository = mock(RouteRecommendationRepository.class);

        PathMetricRepository pathMetricRepository = mock(PathMetricRepository.class);
        when(pathMetricRepository.summariseForPaths(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(telemetry::get).filter(java.util.Objects::nonNull).toList();
        });

        PathHealthService pathHealthService = new PathHealthService(
                pathMetricRepository, networkPathRepository, new PathHealthProperties());

        service = new RouteRecommendationService(
                networkPathRepository, recommendationRepository, pathHealthService);
    }

    private NetworkPath path(long id, String name, Endpoint source, Endpoint destination) {
        NetworkPath path = new NetworkPath();
        path.setId(id);
        path.setPathName(name);
        path.setSourceEndpoint(source);
        path.setDestinationEndpoint(destination);
        return path;
    }

    private void telemetryFor(long pathId, long samples, double avgLatency, double avgLoss) {
        telemetry.put(pathId, new Object[] {
                pathId, avgLatency, avgLoss, avgLatency + 20, avgLoss, samples, Instant.now()
        });
    }

    @Test
    void recommendsTheHealthiestAlternativeBetweenTheSameEndpoints() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath healthy = path(HEALTHY_ALT_ID, "alternative", source, destination);
        NetworkPath worse = path(WORSE_ALT_ID, "backup", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID))
                .thenReturn(List.of(worse, healthy));

        telemetryFor(CURRENT_PATH_ID, 10, 300.0, 5.0);
        telemetryFor(HEALTHY_ALT_ID, 10, 78.0, 0.20);
        telemetryFor(WORSE_ALT_ID, 10, 340.0, 7.5);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getHasAlternative()).isTrue();
        assertThat(recommendation.getCurrentStatus()).isEqualTo(PathStatus.DEGRADED.name());
        assertThat(recommendation.getRecommendedPathId()).isEqualTo(HEALTHY_ALT_ID);
        assertThat(recommendation.getRecommendedStatus()).isEqualTo(PathStatus.HEALTHY.name());
        assertThat(recommendation.getReason()).contains("DEGRADED").contains("Latency");

        ArgumentCaptor<RouteRecommendation> captor = ArgumentCaptor.forClass(RouteRecommendation.class);
        verify(recommendationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecommendedPath().getId()).isEqualTo(HEALTHY_ALT_ID);
    }

    @Test
    void prefersLowerLatencyWhenTwoAlternativesShareAStatus() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath slower = path(HEALTHY_ALT_ID, "slower", source, destination);
        NetworkPath faster = path(WORSE_ALT_ID, "faster", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID))
                .thenReturn(List.of(slower, faster));

        telemetryFor(CURRENT_PATH_ID, 10, 400.0, 20.0);
        telemetryFor(HEALTHY_ALT_ID, 10, 150.0, 1.0);
        telemetryFor(WORSE_ALT_ID, 10, 60.0, 0.2);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getRecommendedPathId()).isEqualTo(WORSE_ALT_ID);
    }

    @Test
    void reportsNoAlternativeWhenEveryCandidateIsWorse() {
        Endpoint source = endpoint(10L, null, null);
        Endpoint destination = endpoint(20L, null, null);

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath worse = path(WORSE_ALT_ID, "backup", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID)).thenReturn(List.of(worse));

        telemetryFor(CURRENT_PATH_ID, 10, 60.0, 0.1);
        telemetryFor(WORSE_ALT_ID, 10, 400.0, 8.0);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getHasAlternative()).isFalse();
        assertThat(recommendation.getRecommendedPathId()).isNull();
        assertThat(recommendation.getReason()).contains("No alternative path");
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void reportsThatNoAlternativesAreConfigured() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");
        NetworkPath current = path(CURRENT_PATH_ID, "only", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID)).thenReturn(List.of());
        telemetryFor(CURRENT_PATH_ID, 10, 60.0, 0.1);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getHasAlternative()).isFalse();
        assertThat(recommendation.getReason()).contains("No alternative paths are configured");
    }

    @Test
    void anUnknownPathProducesNoRecommendation() {
        when(networkPathRepository.findByIdWithDetails(anyLong())).thenReturn(Optional.empty());

        assertThat(service.generateRecommendation(999L)).isNull();
    }

    private Endpoint endpoint(long id, String name, String ip) {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(id);
        endpoint.setName(name);
        endpoint.setIpAddress(ip);
        return endpoint;
    }
}
