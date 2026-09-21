package com.netpath.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netpath.dto.TelemetryRequest;
import com.netpath.entity.*;
import com.netpath.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis is an accelerator, never a source of truth: with the cache unreachable (nothing listens on
 * the configured port, so every Redis operation raises immediately) the API must keep serving
 * correct verdicts by recomputing from PostgreSQL, and ingestion must still persist and
 * reclassify. No 500s, no fabricated fallback data - the same contract a Redis outage would face
 * in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6390",
        // Refused connections still pay a command timeout per operation; keep the outage cheap.
        "spring.data.redis.timeout=500ms"
})
class RedisOutageIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private Long degradedPathId;
    private Long healthyAlternativeId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
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
                new Application("Outage Platform", "Topology under cache outage", operator));

        Endpoint edgeA = endpointRepository.save(new Endpoint(app, "edge-a", "10.0.0.1", "eu-west-1"));
        Endpoint edgeB = endpointRepository.save(new Endpoint(app, "edge-b", "10.0.0.2", "us-east-1"));

        NetworkPath degraded = new NetworkPath(app, edgeA, edgeB, "primary-link", 2, "Primary");
        degradedPathId = networkPathRepository.save(degraded).getId();

        NetworkPath alternative = new NetworkPath(app, edgeA, edgeB, "alternative-link", 3, "Alternative");
        alternative.setIsPrimary(false);
        healthyAlternativeId = networkPathRepository.save(alternative).getId();

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
    void servesTheCurrentVerdictFromPostgresWhenTheCacheIsUnreachable() throws Exception {
        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.metricsCount").value(3))
                .andExpect(jsonPath("$.averageLatencyMs").isNumber());
    }

    @Test
    void ingestionStillPersistsAndReclassifiesWhenTheCacheIsUnreachable() throws Exception {
        sendMetric(degradedPathId, 780, "12.00");

        mockMvc.perform(get("/api/paths/{id}/health", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void theReadSurfaceStaysIntactWithoutTheCache() throws Exception {
        mockMvc.perform(get("/api/paths/{id}", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));

        mockMvc.perform(get("/api/paths/{id}/recommendation", degradedPathId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedPathId").value(healthyAlternativeId));

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaths").value(2))
                .andExpect(jsonPath("$.degradedPaths").value(1));
    }
}
