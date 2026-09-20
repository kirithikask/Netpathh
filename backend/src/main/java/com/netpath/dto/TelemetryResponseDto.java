package com.netpath.dto;

import java.math.BigDecimal;

public class TelemetryResponseDto {
    private Long id;
    private Long pathId;
    private Integer latencyMs;
    private BigDecimal packetLossPct;
    private BigDecimal throughputMbps;
    private String timestamp;

    public TelemetryResponseDto() {}

    public TelemetryResponseDto(Long id, Long pathId, Integer latencyMs,
                                BigDecimal packetLossPct, BigDecimal throughputMbps, String timestamp) {
        this.id = id;
        this.pathId = pathId;
        this.latencyMs = latencyMs;
        this.packetLossPct = packetLossPct;
        this.throughputMbps = throughputMbps;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPathId() { return pathId; }
    public void setPathId(Long pathId) { this.pathId = pathId; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public BigDecimal getPacketLossPct() { return packetLossPct; }
    public void setPacketLossPct(BigDecimal packetLossPct) { this.packetLossPct = packetLossPct; }
    public BigDecimal getThroughputMbps() { return throughputMbps; }
    public void setThroughputMbps(BigDecimal throughputMbps) { this.throughputMbps = throughputMbps; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
