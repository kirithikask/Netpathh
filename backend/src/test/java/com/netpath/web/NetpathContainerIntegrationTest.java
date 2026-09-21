package com.netpath.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netpath.dto.PathHealthResponseDto;
import com.netpath.dto.TelemetryRequest;
import com.netpath.entity.*;
import com.netpath.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same behaviour the H2 suite covers, executed against real PostgreSQL and Redis: Flyway
 * migrations run, the windowed aggregate query runs as real SQL, JPA validation passes, and the
 * cached health object survives a serializer round trip.
 *
 * <p>Skipped entirely on machines without Docker ({@code disabledWithoutDocker}); CI runners have
 * Docker, so the suite runs there on every push.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("container")
class NetpathContainerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private NetworkPathRepository networkPathRepository;

    @Autowired
    private PathMetricRepository pathMetricRepository;

    @Autowired
    private TrafficShiftLogRepository trafficShiftLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long degradedPathId;
    private Long healthyAlternativeId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        trafficShiftLogRepository.deleteAll();
        pathMetricRepository.deleteAll();
        networkPathRepository.deleteAll();
        endpointRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        User operator = userRepository.save(new User(
                "operator@netpath.test",
                passwordEncoder.encode("secret123"),
                "Test Operator",
                UserRole.ADMIN));

        Application app = applicationRepository.save(
                new Application("Container Platform", "Real-infra topology", operator));

        Endpoint edgeA = endpointRepository.save(new Endpoint(app, "edge-a", "10.0.0.1", "eu-west-1"));
        Endpoint edgeB = endpointRepository.save(new Endpoint(app, "edge-b", "10.0.0.2", "us-east-1"));
        Endpoint router = endpointRepository.save(new Endpoint(app, "core-router", "10.0.0.9", "eu-west-1"));

        NetworkPath degraded = new NetworkPath(app, edgeA, edgeB, "primary-link", 2, "Primary");
        degradedPathId = networkPathRepository.save(degraded).getId();

        NetworkPath alternative = new NetworkPath(app, edgeA, edgeB, "alternative-link", 3, "Alternative");
        alternative.setIsPrimary(false);
        healthyAlternativeId = networkPathRepository.save(alternative).getId();

        NetworkPath backup = new NetworkPath(app, edgeA, router, "backup-link", 1, "Backup");
        networkPathRepository.save(backup);

        token = login();

        sendMetric(degradedPathId, 300, "5.00");
        sendMetric(degradedPathId, 310, "5.20");
        sendMetric(degradedPathId, 295, "4.80");

        sendMetric(healthyAlternativeId, 78, "0.20");
        sendMetric(healthyAlternativeId, 81, "0.25");
        sendMetric(healthyAlternativeId, 75, "0.18");
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"operator@netpath.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private Long pathIdNamed(String pathName) {
        return networkPathRepository.findAll().stream()
                .filter(path -> pathName.equals(path.getPathName()))
                .findFirst().orElseThrow().getId();
    }

    private void sendMetric(Long pathId, int latencyMs, String packetLoss) throws Exception {
        TelemetryRequest request = new TelemetryRequest(
                latencyMs, new BigDecimal(packetLoss), new BigDecimal("500.00"));

        mockMvc.perform(post("/api/paths/{id}/metrics", pathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void storeMetricAgedExactlyByMinutes(Long pathId, int minutesAgo,
                                                 int latencyMs, String packetLoss) {
        NetworkPath path = networkPathRepository.findById(pathId).orElseThrow();
        PathMetric metric = new PathMetric(
                path, latencyMs, new BigDecimal(packetLoss), new BigDecimal("500.00"));
        metric.setTimestamp(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        pathMetricRepository.save(metric);
    }

    private Map<String, Long> rowCounts() {
        return Map.of(
                "applications", applicationRepository.count(),
                "endpoints", endpointRepository.count(),
                "network_paths", networkPathRepository.count(),
                "path_metrics", pathMetricRepository.count(),
                "traffic_shift_logs", trafficShiftLogRepository.count(),
                "users", userRepository.count());
    }

    @Test
    void ingestingTelemetryReclassifiesThePathOnRealPostgres() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.metricsCount").value(3));
    }

    @Test
    void dashboardStatsReflectTheRealDatabaseState() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.totalEndpoints").value(3))
                .andExpect(jsonPath("$.totalPaths").value(3))
                .andExpect(jsonPath("$.degradedPaths").value(1))
                .andExpect(jsonPath("$.healthyPaths").value(1))
                .andExpect(jsonPath("$.unknownPaths").value(1));
    }

    /** The read-only contract, proven on the real engine: five reads change zero rows. */
    @Test
    void repeatedRecommendationReadsWriteNoRowsOnRealPostgres() throws Exception {
        Map<String, Long> before = rowCounts();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(get("/api/paths/{id}/recommendation", degradedPathId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recommendedPathId").value(healthyAlternativeId));
        }

        assertThat(rowCounts()).isEqualTo(before);
    }

    /** The window boundary, as real SQL with a {@code timestamp >= :since} predicate. */
    @Test
    void theWindowEdgeAppliesOnRealPostgres() throws Exception {
        Long edgeId = pathIdNamed("backup-link");

        storeMetricAgedExactlyByMinutes(edgeId, 57, 950, "12.00");
        storeMetricAgedExactlyByMinutes(edgeId, 59, 950, "12.00");
        storeMetricAgedExactlyByMinutes(edgeId, 59, 950, "12.00");
        storeMetricAgedExactlyByMinutes(edgeId, 61, 950, "12.00");

        mockMvc.perform(get("/api/paths/{id}/health", edgeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.metricsCount").value(3));
    }

    /**
     * The cache round trip the in-memory stub cannot prove: a GET misses, recomputes from
     * PostgreSQL, stores through the configured serializer, and the next consumer reads a real
     * {@link PathHealthResponseDto} back from Redis.
     */
    @Test
    void hotStateRoundTripsThroughRealRedis() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));

        Object cached = redisTemplate.opsForValue().get("path:health:" + degradedPathId);

        assertThat(cached).isInstanceOf(PathHealthResponseDto.class);
        assertThat(((PathHealthResponseDto) cached).getStatus()).isEqualTo("DEGRADED");
        assertThat(((PathHealthResponseDto) cached).getMetricsCount()).isEqualTo(3);
    }
}
