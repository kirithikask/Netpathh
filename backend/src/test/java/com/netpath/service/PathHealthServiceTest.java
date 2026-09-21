package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.PathTelemetrySummary;
import com.netpath.entity.PathStatus;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The classification rule is pure, so these tests need no mocks and no database.
 */
class PathHealthServiceTest {

    private PathHealthProperties properties;
    private PathMetricRepository pathMetricRepository;
    private PathHealthService service;

    @BeforeEach
    void setUp() {
        properties = new PathHealthProperties();
        pathMetricRepository = mock(PathMetricRepository.class);
        service = new PathHealthService(
                pathMetricRepository, mock(NetworkPathRepository.class), properties);
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

    /**
     * The four threshold boundaries, each probed one step below, exactly on, and one step above.
     * Averages drive DEGRADED, worst samples drive DOWN, and both boundaries are inclusive - the
     * configured number itself has to read literally, not as "strictly greater than".
     */
    static Stream<Arguments> thresholdBoundaries() {
        return Stream.of(
                // [description, samples, avgLatency, avgLoss, maxLatency, maxLoss, expected]
                // Average latency around the DEGRADED boundary; max kept below the DOWN boundary.
                Arguments.of("99 ms average is healthy", 10, 99.0, 0.2, 120.0, 0.3, PathStatus.HEALTHY),
                Arguments.of("100 ms average is degraded", 10, 100.0, 0.2, 120.0, 0.3, PathStatus.DEGRADED),
                Arguments.of("101 ms average is degraded", 10, 101.0, 0.2, 120.0, 0.3, PathStatus.DEGRADED),
                // Average packet loss around the DEGRADED boundary.
                Arguments.of("0.49% average loss is healthy", 10, 60.0, 0.49, 80.0, 0.6, PathStatus.HEALTHY),
                Arguments.of("0.5% average loss is degraded", 10, 60.0, 0.5, 80.0, 0.6, PathStatus.DEGRADED),
                Arguments.of("0.51% average loss is degraded", 10, 60.0, 0.51, 80.0, 0.6, PathStatus.DEGRADED),
                // Worst samples drive only DOWN: below the severe boundary, a spike with healthy
                // averages is not enough to degrade the path.
                Arguments.of("499 ms worst latency with healthy averages is healthy", 10, 60.0, 0.2, 499.0, 0.3, PathStatus.HEALTHY),
                Arguments.of("500 ms worst latency is down", 10, 60.0, 0.2, 500.0, 0.3, PathStatus.DOWN),
                Arguments.of("501 ms worst latency is down", 10, 60.0, 0.2, 501.0, 0.3, PathStatus.DOWN),
                // Worst packet loss around the DOWN boundary, same asymmetry.
                Arguments.of("9.9% worst loss with healthy averages is healthy", 10, 60.0, 0.2, 80.0, 9.9, PathStatus.HEALTHY),
                Arguments.of("10% worst loss is down", 10, 60.0, 0.2, 80.0, 10.0, PathStatus.DOWN),
                Arguments.of("10.1% worst loss is down", 10, 60.0, 0.2, 80.0, 10.1, PathStatus.DOWN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("thresholdBoundaries")
    void classifiesTheThresholdBoundaries(String description, long samples, Double avgLatency,
                                          Double avgLoss, Double maxLatency, Double maxLoss,
                                          PathStatus expected) {
        assertThat(classify(samples, avgLatency, avgLoss, maxLatency, maxLoss)).isEqualTo(expected);
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

    // --- current-health window ------------------------------------------------------------------

    /**
     * The aggregate must be bounded by the configured window, otherwise every sample ever ingested
     * is averaged into "now".
     */
    @Test
    void aggregatesOnlySamplesInsideTheConfiguredWindow() {
        properties.setWindowMinutes(30);
        when(pathMetricRepository.summariseForPaths(anyCollection(), any())).thenReturn(List.of());

        Instant before = Instant.now().minus(Duration.ofMinutes(30));
        service.summarise(1L);
        Instant after = Instant.now().minus(Duration.ofMinutes(30));

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(pathMetricRepository).summariseForPaths(anyCollection(), since.capture());

        assertThat(since.getValue()).isBetween(before, after);
    }

    /**
     * A path whose samples have all aged out produces no aggregate row, and must be reported as
     * UNKNOWN rather than keeping its last verdict.
     */
    @Test
    void aPathWithNoSamplesInsideTheWindowHasNoSummary() {
        when(pathMetricRepository.summariseForPaths(anyCollection(), any())).thenReturn(List.of());

        PathTelemetrySummary summary = service.summarise(1L);

        assertThat(summary.pathId()).isEqualTo(1L);
        assertThat(summary.sampleCount()).isZero();
        assertThat(service.classify(summary)).isEqualTo(PathStatus.UNKNOWN);
    }

    @Test
    void readingTheWindowStartUsesTheConfiguredMinutes() {
        properties.setWindowMinutes(15);

        Instant expected = Instant.now().minus(Duration.ofMinutes(15));
        assertThat(Duration.between(expected, service.currentWindowStart())).isLessThan(Duration.ofSeconds(2));
    }
}
