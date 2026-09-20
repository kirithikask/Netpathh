package com.netpath.controller;

import com.netpath.dto.*;
import com.netpath.entity.PathStatus;
import com.netpath.service.NetworkPathService;
import com.netpath.service.RouteRecommendationService;
import com.netpath.service.TelemetryService;
import com.netpath.service.TrafficShiftService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/paths")
public class NetworkPathController {

    private final NetworkPathService pathService;
    private final TelemetryService telemetryService;
    private final RouteRecommendationService recommendationService;
    private final TrafficShiftService trafficShiftService;

    public NetworkPathController(NetworkPathService pathService,
                                 TelemetryService telemetryService,
                                 RouteRecommendationService recommendationService,
                                 TrafficShiftService trafficShiftService) {
        this.pathService = pathService;
        this.telemetryService = telemetryService;
        this.recommendationService = recommendationService;
        this.trafficShiftService = trafficShiftService;
    }

    @GetMapping
    public ResponseEntity<Page<NetworkPathDto>> getAllPaths(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PathStatus status) {

        PageRequest pageRequest = PageRequests.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(status != null
                ? pathService.getPathsByStatus(status, pageRequest)
                : pathService.getAllPaths(pageRequest));
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<Page<NetworkPathDto>> getPathsByApplication(
            @PathVariable Long applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(pathService.getPathsByApplication(
                applicationId, PageRequests.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NetworkPathDto> getPathById(@PathVariable Long id) {
        return ResponseEntity.ok(pathService.getPathById(id));
    }

    @PostMapping
    public ResponseEntity<NetworkPathDto> createPath(@Valid @RequestBody NetworkPathDto dto,
                                                      @RequestParam Long applicationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pathService.createPath(dto, applicationId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NetworkPathDto> updatePath(@PathVariable Long id,
                                                      @Valid @RequestBody NetworkPathDto dto) {
        return ResponseEntity.ok(pathService.updatePath(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePath(@PathVariable Long id) {
        pathService.deletePath(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<PathHealthResponseDto> getPathHealth(@PathVariable Long id) {
        return ResponseEntity.ok(pathService.getHealth(id));
    }

    @PostMapping("/{id}/metrics")
    public ResponseEntity<PathHealthResponseDto> ingestTelemetry(
            @PathVariable Long id,
            @Valid @RequestBody TelemetryRequest request) {

        return ResponseEntity.ok(telemetryService.ingest(id, request));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<Page<TelemetryResponseDto>> getMetrics(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(telemetryService.history(id, from, to, PageRequests.of(page, size)));
    }

    @GetMapping("/{id}/metrics/recent")
    public ResponseEntity<List<TelemetryResponseDto>> getRecentMetrics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(telemetryService.recent(id, limit));
    }

    @GetMapping("/{id}/recommendation")
    public ResponseEntity<RouteRecommendationDto> getRecommendation(@PathVariable Long id) {
        RouteRecommendationDto recommendation = recommendationService.generateRecommendation(id);
        if (recommendation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(recommendation);
    }

    @GetMapping("/{id}/shifts")
    public ResponseEntity<List<TrafficShiftResponse>> getShiftHistory(@PathVariable Long id) {
        return ResponseEntity.ok(trafficShiftService.getShiftHistory(id));
    }

    @PostMapping("/{id}/shift")
    public ResponseEntity<TrafficShiftResponse> simulateShift(
            @PathVariable Long id,
            @Valid @RequestBody TrafficShiftRequest request) {

        return ResponseEntity.ok(trafficShiftService.simulateShift(id, request));
    }
}
