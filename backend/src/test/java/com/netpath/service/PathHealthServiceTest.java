package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.PathTelemetrySummary;
import com.netpath.entity.PathStatus;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The classification rule is pure, so these tests need no mocks and no database.
 */
class PathHealthServiceTest {

    private PathHealthProperties properties;
    private PathHealthService service;

    @BeforeEach
    void setUp() {
        properties = new PathHealthProperties();
        service = new PathHealthService(
                mock(PathMetricRepository.class), mock(NetworkPathRepository.class), properties);
    }

    private PathTelemetrySummary summary(long samples, Double avgLatency, Double avgLoss,
                                         Double maxLatency, Double maxLoss) {
        return new PathTelemetrySummary(
                1L, samples, avgLatency, avgLoss, maxLatency, maxLoss, Instant.now());
    }

    private PathStatus classify(long samples, Double avgLatency, Double avgLoss,
                                Double maxLatency, Double maxLoss) {
        return service.classify(summary(samples, avgLatency, avgLoss, maxLatency, maxLoss));
    }

    @Test
    void reportsUnknownWhenThereAreTooFewSamples() {
        assertThat(classify(2, 20.0, 0.0, 30.0, 0.0)).isEqualTo(PathStatus.UNKNOWN);
        assertThat(classify(0, null, null, null, null)).isEqualTo(PathStatus.UNKNOWN);
    }

    @Test
    void reportsHealthyInsideBothBoundaries() {
        assertThat(classify(10, 42.0, 0.10, 60.0, 0.4)).isEqualTo(PathStatus.HEALTHY);
    }

    @Test
    void degradesWhenAverageLatencyReachesTheWarningBoundary() {
        assertThat(classify(10, 100.0, 0.2, 120.0, 0.3)).isEqualTo(PathStatus.DEGRADED);
        assertThat(classify(10, 260.0, 0.2, 300.0, 0.3)).isEqualTo(PathStatus.DEGRADED);
    }

    @Test
    void degradesWhenAveragePacketLossReachesTheWarningBoundary() {
        assertThat(classify(10, 30.0, 0.5, 40.0, 0.6)).isEqualTo(PathStatus.DEGRADED);
    }

    @Test
    void goesDownWhenTheWorstLatencyReachesTheSevereBoundaryEvenIfTheAverageIsFine() {
        assertThat(classify(12, 90.0, 0.2, 500.0, 0.3)).isEqualTo(PathStatus.DOWN);
    }

    @Test
    void goesDownWhenTheWorstPacketLossReachesTheSevereBoundary() {
        assertThat(classify(12, 40.0, 1.0, 55.0, 12.5)).isEqualTo(PathStatus.DOWN);
    }

    @Test
    void bothBoundariesAreInclusive() {
        // Exactly on the boundary counts as crossing it, so configured numbers read literally.
        assertThat(classify(5, 100.0, 0.1, 100.0, 0.1)).isEqualTo(PathStatus.DEGRADED);
        assertThat(classify(5, 100.0, 0.1, 500.0, 0.1)).isEqualTo(PathStatus.DOWN);
    }

    @Test
    void usesConfiguredBoundariesRatherThanHardCodedValues() {
        properties.getThresholds().setDegradedLatencyMs(10);
        properties.getThresholds().setDownLatencyMs(20);
        properties.getThresholds().setDegradedPacketLossPct(0.01);

        assertThat(classify(5, 15.0, 0.0, 15.0, 0.0)).isEqualTo(PathStatus.DEGRADED);
        assertThat(classify(5, 5.0, 0.0, 25.0, 0.0)).isEqualTo(PathStatus.DOWN);
        assertThat(classify(5, 5.0, 0.0, 5.0, 0.0)).isEqualTo(PathStatus.HEALTHY);
    }

    @Test
    void raisesTheMinimumSampleRequirementFromConfiguration() {
        properties.getThresholds().setMinMetricsForEvaluation(10);

        assertThat(classify(9, 20.0, 0.1, 25.0, 0.1)).isEqualTo(PathStatus.UNKNOWN);
        assertThat(classify(10, 20.0, 0.1, 25.0, 0.1)).isEqualTo(PathStatus.HEALTHY);
    }

    @Test
    void doesNotThrowWhenAMetricIsAbsent() {
        assertThat(classify(5, null, null, null, null)).isEqualTo(PathStatus.HEALTHY);
    }
}
