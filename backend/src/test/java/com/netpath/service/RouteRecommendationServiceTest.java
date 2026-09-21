package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.RouteRecommendationDto;
import com.netpath.entity.Endpoint;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private RouteRecommendationService service;

    /** Telemetry rows keyed by path, served through the single aggregate query. */
    private final Map<Long, Object[]> telemetry = new HashMap<>();

    @BeforeEach
    void setUp() {
        networkPathRepository = mock(NetworkPathRepository.class);

        PathMetricRepository pathMetricRepository = mock(PathMetricRepository.class);
        when(pathMetricRepository.summariseForPaths(anyCollection(), any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(telemetry::get).filter(java.util.Objects::nonNull).toList();
        });

        PathHealthService pathHealthService = new PathHealthService(
                pathMetricRepository, networkPathRepository, new PathHealthProperties());

        service = new RouteRecommendationService(networkPathRepository, pathHealthService);
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
    }

    /**
     * The endpoint is a GET, so evaluating it must not write anything - not a recommendation row and
     * not a timestamp touch on the path. Nothing in the repository may be saved by a read.
     */
    @Test
    void evaluatingARecommendationWritesNothing() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath healthy = path(HEALTHY_ALT_ID, "alternative", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID))
                .thenReturn(List.of(healthy));

        telemetryFor(CURRENT_PATH_ID, 10, 300.0, 5.0);
        telemetryFor(HEALTHY_ALT_ID, 10, 78.0, 0.20);

        // The telemetry above classifies this path DEGRADED, which is what a persisting read would
        // have written. The entity still carries its untouched default, so nothing was stored.
        assertThat(service.generateRecommendation(CURRENT_PATH_ID).getCurrentStatus())
                .isEqualTo(PathStatus.DEGRADED.name());
        service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(current.getStatus()).isEqualTo(PathStatus.UNKNOWN);
        verify(networkPathRepository, never()).save(any());
        verify(networkPathRepository, never()).saveAll(any());
        verify(networkPathRepository, never()).delete(any());
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

    /**
     * Equal status is not a tie that blocks the recommendation: among siblings of the same rank,
     * lower average latency wins, so a DEGRADED current path can be moved to a DEGRADED sibling
     * that is measurably faster.
     */
    @Test
    void recommendsAnEqualStatusAlternativeWithLowerLatency() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath fasterDegraded = path(HEALTHY_ALT_ID, "faster-degraded", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID))
                .thenReturn(List.of(fasterDegraded));

        telemetryFor(CURRENT_PATH_ID, 10, 300.0, 5.0);
        telemetryFor(HEALTHY_ALT_ID, 10, 150.0, 1.0);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getHasAlternative()).isTrue();
        assertThat(recommendation.getRecommendedPathId()).isEqualTo(HEALTHY_ALT_ID);
        assertThat(recommendation.getRecommendedStatus()).isEqualTo(PathStatus.DEGRADED.name());
        assertThat(recommendation.getReason()).contains("Latency");
    }

    /**
     * "Measurably better" means better in some dimension: an identical sibling must not be
     * recommended, or every poll would point the operator sideways at no gain.
     */
    @Test
    void keepsTheCurrentPathWhenLatencyAndLossAreEqual() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath twin = path(HEALTHY_ALT_ID, "twin", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID))
                .thenReturn(List.of(twin));

        telemetryFor(CURRENT_PATH_ID, 10, 300.0, 5.0);
        telemetryFor(HEALTHY_ALT_ID, 10, 300.0, 5.0);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getHasAlternative()).isFalse();
        assertThat(recommendation.getRecommendedPathId()).isNull();
        assertThat(recommendation.getReason()).contains("No alternative path is currently healthier");
    }

    /**
     * Latency ties break on packet loss - the third documented dimension of the ranking rule
     * (status, then average latency, then packet loss).
     */
    @Test
    void breaksALatencyTieOnPacketLoss() {
        Endpoint source = endpoint(10L, "edge-a", "10.0.0.1");
        Endpoint destination = endpoint(20L, "edge-b", "10.0.0.2");

        NetworkPath current = path(CURRENT_PATH_ID, "primary", source, destination);
        NetworkPath cleaner = path(HEALTHY_ALT_ID, "cleaner", source, destination);

        when(networkPathRepository.findByIdWithDetails(CURRENT_PATH_ID)).thenReturn(Optional.of(current));
        when(networkPathRepository.findAlternativePaths(10L, 20L, CURRENT_PATH_ID))
                .thenReturn(List.of(cleaner));

        telemetryFor(CURRENT_PATH_ID, 10, 150.0, 2.0);
        telemetryFor(HEALTHY_ALT_ID, 10, 150.0, 0.3);

        RouteRecommendationDto recommendation = service.generateRecommendation(CURRENT_PATH_ID);

        assertThat(recommendation.getHasAlternative()).isTrue();
        assertThat(recommendation.getRecommendedPathId()).isEqualTo(HEALTHY_ALT_ID);
        assertThat(recommendation.getReason()).contains("Packet loss");
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
