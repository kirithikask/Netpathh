package com.netpath.controller;

import com.netpath.config.PathHealthProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the runtime classification boundaries. The console renders these next to each path so an
 * operator can see why a path was classified, instead of a UI copy drifting from the backend.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final PathHealthProperties pathHealthProperties;

    public ConfigController(PathHealthProperties pathHealthProperties) {
        this.pathHealthProperties = pathHealthProperties;
    }

    @GetMapping("/path-health")
    public ResponseEntity<Map<String, Object>> getPathHealthConfig() {
        PathHealthProperties.Thresholds thresholds = pathHealthProperties.getThresholds();

        return ResponseEntity.ok(Map.of(
                "degradedLatencyMs", thresholds.getDegradedLatencyMs(),
                "downLatencyMs", thresholds.getDownLatencyMs(),
                "degradedPacketLossPct", thresholds.getDegradedPacketLossPct(),
                "downPacketLossPct", thresholds.getDownPacketLossPct(),
                "minMetricsForEvaluation", thresholds.getMinMetricsForEvaluation(),
                "windowMinutes", pathHealthProperties.getWindowMinutes(),
                "cacheTtlSeconds", pathHealthProperties.getCache().getTtlSeconds()
        ));
    }
}
