package com.netpath.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netpath.dto.TelemetryRequest;
import com.netpath.dto.TrafficShiftRequest;
import com.netpath.entity.*;
import com.netpath.repository.*;
import com.netpath.support.InMemoryRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NetpathApiIntegrationTest {

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

    /**
     * Redis is replaced with an in-memory stand-in so the suite runs without external services.
     */
    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private Long degradedPathId;
    private Long healthyAlternativeId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryRedis.install(redisTemplate);

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
                new Application("Integration Platform", "Test topology", operator));

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

    /**
     * Writes telemetry directly so a timestamp can be placed in the past. The API always stamps the
     * current instant, which is exactly the state these window tests need to step outside of.
     */
    private void storeMetricsAgedBy(Long pathId, int samples, int hoursAgo, int latencyMs, String packetLoss) {
        storeMetricsAgedByMinutes(pathId, samples, hoursAgo * 60, latencyMs, packetLoss);
    }

    private void storeMetricsAgedByMinutes(Long pathId, int samples, int minutesAgo,
                                           int latencyMs, String packetLoss) {
        NetworkPath path = networkPathRepository.findById(pathId).orElseThrow();
        Instant start = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);

        for (int i = 0; i < samples; i++) {
            PathMetric metric = new PathMetric(
                    path, latencyMs, new BigDecimal(packetLoss), new BigDecimal("500.00"));
            metric.setTimestamp(start.plus(i, ChronoUnit.MINUTES));
            pathMetricRepository.save(metric);
        }
    }

    /** One sample stamped at exactly {@code minutesAgo} - the unit the window edge is stated in. */
    private void storeMetricAgedExactlyByMinutes(Long pathId, int minutesAgo,
                                                 int latencyMs, String packetLoss) {
        NetworkPath path = networkPathRepository.findById(pathId).orElseThrow();
        PathMetric metric = new PathMetric(
                path, latencyMs, new BigDecimal(packetLoss), new BigDecimal("500.00"));
        metric.setTimestamp(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        pathMetricRepository.save(metric);
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

    @Test
    void rejectsUnauthenticatedAccessToProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/paths")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void exposesTheHealthAndApiDocsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void ingestingTelemetryUpdatesThePathHealth() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.metricsCount").value(3));
    }

    @Test
    void aPathWithNoTelemetryIsReportedAsUnknown() throws Exception {
        NetworkPath cold = new NetworkPath(
                applicationRepository.findAll().getFirst(),
                endpointRepository.findAll().get(2),
                endpointRepository.findAll().get(1),
                "cold-link", 1, "Never reported telemetry");
        cold.setIsPrimary(false);
        Long coldId = networkPathRepository.save(cold).getId();

        mockMvc.perform(get("/api/paths/{id}/health", coldId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"));
    }

    /**
     * Current health is computed over a recent window, so telemetry that has aged out stops
     * deciding the verdict. Without that, a bad hour last month would hold a recovered path
     * DEGRADED - or a dead path "healthy" - forever.
     */
    @Test
    void telemetryOlderThanTheHealthWindowNoLongerDecidesTheCurrentStatus() throws Exception {
        Long backupId = pathIdNamed("backup-link");
        storeMetricsAgedBy(backupId, 5, 2, 900, "20.00");

        mockMvc.perform(get("/api/paths/{id}/health", backupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                // Zero samples inside the window is the mechanism, so assert it directly.
                .andExpect(jsonPath("$.metricsCount").value(0));

        // The samples are still stored: history is retained, it just is not current health.
        assertThat(pathMetricRepository.findByPathIdOrderByTimestampDesc(
                backupId, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(5);
    }

    /**
     * The counterpart: ancient samples are ignored while recent ones still classify the path, even
     * when the two would disagree.
     */
    @Test
    void onlySamplesInsideTheHealthWindowFeedTheClassification() throws Exception {
        Long backupId = pathIdNamed("backup-link");

        // Four catastrophic samples from two hours ago, then three healthy ones now.
        storeMetricsAgedBy(backupId, 4, 2, 900, "20.00");
        sendMetric(backupId, 44, "0.10");
        sendMetric(backupId, 46, "0.12");
        sendMetric(backupId, 42, "0.08");

        // Averaging all seven would put latency near 530 ms, which is DOWN.
        mockMvc.perform(get("/api/paths/{id}/health", backupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.metricsCount").value(3));
    }

    /**
     * The window edge, in both directions, without sleeping: three samples 59 minutes old still
     * decide the verdict (DOWN, and exactly 3 in-window samples), while the sibling sample 61
     * minutes old is excluded from the aggregate. If the window edge were exclusive or drifted,
     * the counts and the verdict would both move.
     */
    @Test
    void samplesJustInsideTheWindowDecideAndTheOneJustOutsideIsExcluded() throws Exception {
        Long edgeId = pathIdNamed("backup-link");

        // Three samples 57-59 minutes old: all inside the window, so the path is DOWN with exactly
        // three samples counted. A 61-minute-old sample carries the same severe reading but must be
        // excluded - if it were aggregated the count would read four.
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

    /** The counterpart: one minute past the window, the same severe samples stop deciding. */
    @Test
    void samplesJustOutsideTheWindowLeaveThePathUnknown() throws Exception {
        Long edgeId = pathIdNamed("backup-link");

        storeMetricAgedExactlyByMinutes(edgeId, 61, 950, "12.00");
        storeMetricAgedExactlyByMinutes(edgeId, 62, 950, "12.00");
        storeMetricAgedExactlyByMinutes(edgeId, 63, 950, "12.00");

        mockMvc.perform(get("/api/paths/{id}/health", edgeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.metricsCount").value(0));
    }

    @Test
    void severeTelemetryMarksAPathAsDown() throws Exception {
        sendMetric(degradedPathId, 780, "12.00");

        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void rejectsInvalidTelemetry() throws Exception {
        mockMvc.perform(post("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latencyMs\":-5,\"packetLossPct\":250}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsMetricHistoryWithPaginationAndTimeRange() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2000-01-01T00:00:00Z")
                        .param("to", "2000-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void recommendsAHealthierAlternativePath() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/recommendation", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("DEGRADED"))
                .andExpect(jsonPath("$.recommendedPathId").value(healthyAlternativeId))
                .andExpect(jsonPath("$.recommendedStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.hasAlternative").value(true))
                .andExpect(jsonPath("$.reason").isNotEmpty());
    }

    /**
     * Recommendation evaluation is a pure read. A dashboard polling it, an operator refreshing it, or
     * a crawler hitting it must all leave the database byte-for-byte as it was.
     */
    @Test
    void evaluatingARecommendationRepeatedlyWritesNoRows() throws Exception {
        Map<String, Long> before = rowCounts();

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get("/api/paths/{id}/recommendation", degradedPathId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recommendedPathId").value(healthyAlternativeId));
        }

        assertThat(rowCounts()).isEqualTo(before);
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
    void recordsASimulatedTrafficShift() throws Exception {
        TrafficShiftRequest request = new TrafficShiftRequest("Degraded primary link");
        request.setRecommendedPathId(healthyAlternativeId);

        mockMvc.perform(post("/api/paths/{id}/shift", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIMULATED"))
                .andExpect(jsonPath("$.newPathId").value(healthyAlternativeId));

        assertThat(trafficShiftLogRepository.findAll()).hasSize(1);

        mockMvc.perform(get("/api/shifts/recent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void returnsAStructuredErrorForAMissingPath() throws Exception {
        mockMvc.perform(get("/api/paths/{id}", 987654)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void dashboardStatsReflectTheStoredPathStatuses() throws Exception {
        String body = mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> stats = objectMapper.readValue(body, Map.class);

        assertThat(stats.get("totalApplications")).isEqualTo(1);
        assertThat(stats.get("totalEndpoints")).isEqualTo(3);
        assertThat(stats.get("totalPaths")).isEqualTo(3);
        assertThat(stats.get("degradedPaths")).isEqualTo(1);
        assertThat(stats.get("healthyPaths")).isEqualTo(1);
        assertThat(stats.get("unknownPaths")).isEqualTo(1);
    }

    // --- malformed and out-of-domain input ------------------------------------------------------

    @Test
    void namesTheAcceptedValuesForAnUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/paths")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("BOGUS")))
                .andExpect(jsonPath("$.message").value(containsString("HEALTHY")));
    }

    @Test
    void rejectsIdentifiersThatAreNotNumbers() throws Exception {
        mockMvc.perform(get("/api/paths/not-a-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not-a-number")));

        mockMvc.perform(get("/api/paths/{id}/metrics", "abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/endpoints/application/xyz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Larger than a Long cannot overflow into a silent lookup miss.
        mockMvc.perform(get("/api/paths/99999999999999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPageSizesOutsideTheSupportedRangeOnEveryListEndpoint() throws Exception {
        Long applicationId = applicationRepository.findAll().getFirst().getId();

        String[] listEndpoints = {
                "/api/paths",
                "/api/endpoints",
                "/api/endpoints/application/" + applicationId,
                "/api/paths/" + degradedPathId + "/metrics",
                "/api/paths/application/" + applicationId
        };

        for (String endpoint : listEndpoints) {
            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + token)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + token)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + token)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + token)
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void rejectsLimitsOutsideTheSupportedRange() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/metrics/recent", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/paths/{id}/metrics/recent", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/shifts/recent")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/shifts/recent")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "51"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/shifts/recent")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "50"))
                .andExpect(status().isOk());
    }

    @Test
    void refusesToSortByAPropertyTheEndpointDoesNotHave() throws Exception {
        mockMvc.perform(get("/api/endpoints")
                        .header("Authorization", "Bearer " + token)
                        .param("sortBy", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("bogus")));

        // A property that used to exist, or belongs to another resource, fails the same way.
        mockMvc.perform(get("/api/endpoints")
                        .header("Authorization", "Bearer " + token)
                        .param("sortBy", "latencyMs"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/endpoints")
                        .header("Authorization", "Bearer " + token)
                        .param("sortBy", "region")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].region").value("eu-west-1"));
    }

    @Test
    void distinguishesUnreadableBodiesFromWrongMediaTypesAndUnknownRoutes() throws Exception {
        mockMvc.perform(post("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latencyMs\": 12,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("latencyMs=12"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        mockMvc.perform(get("/api/nothing-here")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(patch("/api/paths")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsAnInvertedTimeRangeAndAValueThatIsNotATimestamp() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("from")));

        mockMvc.perform(get("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void writingTelemetryForAnUnknownPathChangesNothing() throws Exception {
        long before = pathMetricRepository.count();

        mockMvc.perform(post("/api/paths/{id}/metrics", 987654)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latencyMs\":10,\"packetLossPct\":1.0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertThat(pathMetricRepository.count()).isEqualTo(before);
    }

    @Test
    void aBodyMissingARequiredMetricIsRejectedWithTheFieldMessage() throws Exception {
        mockMvc.perform(post("/api/paths/{id}/metrics", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packetLossPct\":1.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Latency is required")));
    }

    // --- state synchronisation ------------------------------------------------------------------

    @Test
    void cacheDetailListAndDashboardAllAgreeAfterDegradation() throws Exception {
        sendMetric(degradedPathId, 790, "13.00");

        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("DOWN"));

        mockMvc.perform(get("/api/paths/{id}", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.metricsCount").value(4));

        String listBody = mockMvc.perform(get("/api/paths")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(listBody).get("content").get(0).get("id").asLong())
                .isEqualTo(degradedPathId);

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.downPaths").value(1))
                .andExpect(jsonPath("$.degradedPaths").value(0))
                .andExpect(jsonPath("$.healthyPaths").value(1))
                .andExpect(jsonPath("$.unknownPaths").value(1));
    }

    @Test
    void aColdCacheRebuildsTheSameVerdictFromPostgres() throws Exception {
        sendMetric(degradedPathId, 790, "13.00");

        // Installing again drops the hot state, so the next read is a cache miss.
        InMemoryRedis.install(redisTemplate);

        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.metricsCount").value(4));
    }

    /**
     * Deleting a path has to clean up everything that referred to it: its telemetry rows and the hot
     * state cached from them. Otherwise the health endpoint keeps serving a path that no longer exists.
     */
    @Test
    void deletingAPathRemovesItsTelemetryAndItsHotState() throws Exception {
        Long backupId = networkPathRepository.findAll().stream()
                .filter(path -> "backup-link".equals(path.getPathName()))
                .findFirst().orElseThrow().getId();

        // Three samples is the documented minimum for a verdict, so the path is cached as HEALTHY.
        sendMetric(backupId, 44, "0.10");
        sendMetric(backupId, 46, "0.12");
        sendMetric(backupId, 42, "0.08");
        long metricsBefore = pathMetricRepository.count();

        mockMvc.perform(get("/api/paths/{id}/health", backupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"));

        mockMvc.perform(delete("/api/paths/{id}", backupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/paths/{id}", backupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/paths/{id}/health", backupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // The cascade is the database's job in production and the mapping's in dev; assert the rows.
        assertThat(pathMetricRepository.findByPathIdOrderByTimestampDesc(
                backupId, PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(pathMetricRepository.count()).isEqualTo(metricsBefore - 3);

        mockMvc.perform(get("/api/paths")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalPaths").value(2))
                .andExpect(jsonPath("$.healthyPaths").value(1));
    }

    /**
     * A path that has been shifted away from is named by a shift log, which may not block its
     * deletion.
     */
    @Test
    void deletingAPathSurvivesTheShiftLogsThatMentionIt() throws Exception {
        TrafficShiftRequest request = new TrafficShiftRequest("Drain the alternative before maintenance");
        request.setRecommendedPathId(healthyAlternativeId);

        mockMvc.perform(post("/api/paths/{id}/shift", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/paths/{id}", healthyAlternativeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // The audit line outlives the path it pointed at, with that side blanked out.
        String shifts = mockMvc.perform(get("/api/shifts/recent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(shifts).get(0).get("newPathId").isNull()).isTrue();
        assertThat(objectMapper.readTree(shifts).get(0).get("pathId").asLong()).isEqualTo(degradedPathId);

        // And the recommendation is simply recomputed without the vanished alternative.
        mockMvc.perform(get("/api/paths/{id}/recommendation", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAlternative").value(false));
    }

    /**
     * Deleting an endpoint has to take the whole chain with it: the paths that ran through it, their
     * telemetry, the recommendations and shift records that name them, and the hot state built on
     * them. A path is meaningless without both of its ends.
     */
    @Test
    void deletingAnEndpointRemovesThePathsThatRunThroughIt() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/recommendation", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        TrafficShiftRequest request = new TrafficShiftRequest("Drain the link before decommissioning");
        request.setRecommendedPathId(healthyAlternativeId);

        mockMvc.perform(post("/api/paths/{id}/shift", degradedPathId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Long edgeB = endpointRepository.findAll().stream()
                .filter(endpoint -> "edge-b".equals(endpoint.getName()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(delete("/api/endpoints/{id}", edgeB)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/paths/{id}", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/paths/{id}/health", healthyAlternativeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/paths")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pathName").value("backup-link"));

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalEndpoints").value(2))
                .andExpect(jsonPath("$.totalPaths").value(1));

        assertThat(pathMetricRepository.count()).isZero();
        assertThat(trafficShiftLogRepository.count()).isZero();
    }

    @Test
    void deletingAnApplicationRemovesItsWholeEstate() throws Exception {
        Long applicationId = applicationRepository.findAll().getFirst().getId();

        mockMvc.perform(delete("/api/applications/{id}", applicationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.totalEndpoints").value(0))
                .andExpect(jsonPath("$.totalPaths").value(0));

        assertThat(endpointRepository.count()).isZero();
        assertThat(networkPathRepository.count()).isZero();
        assertThat(pathMetricRepository.count()).isZero();
    }

    @Test
    void listsPathsWithTheirTelemetrySummaryInASinglePageQuery() throws Exception {
        mockMvc.perform(get("/api/paths")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/paths/{id}", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.averageLatencyMs").isNumber())
                .andExpect(jsonPath("$.averagePacketLossPct").isNumber())
                .andExpect(jsonPath("$.metricsCount").value(3))
                .andExpect(jsonPath("$.sourceEndpointName").value("edge-a"))
                .andExpect(jsonPath("$.destinationEndpointName").value("edge-b"));
    }
}
