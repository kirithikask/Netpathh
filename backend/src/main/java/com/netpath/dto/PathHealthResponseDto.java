package com.netpath.dto;

import java.math.BigDecimal;

public class PathHealthResponseDto {

    private Long pathId;
    private String status;
    private Double averageLatencyMs;
    private Double averagePacketLossPct;
    private Double maxLatencyMs;
    private Double maxPacketLossPct;
    private Long metricsCount;
    private String lastUpdated;

    public PathHealthResponseDto() {}

    public PathHealthResponseDto(Long pathId, String status, Double averageLatencyMs,
                                  Double averagePacketLossPct, Double maxLatencyMs,
                                  Double maxPacketLossPct, Long metricsCount, String lastUpdated) {
        this.pathId = pathId;
        this.status = status;
        this.averageLatencyMs = averageLatencyMs;
        this.averagePacketLossPct = averagePacketLossPct;
        this.maxLatencyMs = maxLatencyMs;
        this.maxPacketLossPct = maxPacketLossPct;
        this.metricsCount = metricsCount;
        this.lastUpdated = lastUpdated;
    }

    public Long getPathId() { return pathId; }
    public void setPathId(Long pathId) { this.pathId = pathId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getAverageLatencyMs() { return averageLatencyMs; }
    public void setAverageLatencyMs(Double averageLatencyMs) { this.averageLatencyMs = averageLatencyMs; }
    public Double getAveragePacketLossPct() { return averagePacketLossPct; }
    public void setAveragePacketLossPct(Double averagePacketLossPct) { this.averagePacketLossPct = averagePacketLossPct; }
    public Double getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(Double maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
    public Double getMaxPacketLossPct() { return maxPacketLossPct; }
    public void setMaxPacketLossPct(Double maxPacketLossPct) { this.maxPacketLossPct = maxPacketLossPct; }
    public Long getMetricsCount() { return metricsCount; }
    public void setMetricsCount(Long metricsCount) { this.metricsCount = metricsCount; }
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
}
