package com.netpath.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class TelemetryRequest {

    @NotNull(message = "Latency is required")
    @Min(value = 0, message = "Latency must be non-negative")
    @Max(value = 100000, message = "Latency exceeds maximum allowed value (100000ms)")
    private Integer latencyMs;

    @NotNull(message = "Packet loss is required")
    @Min(value = 0, message = "Packet loss must be non-negative")
    @Max(value = 100, message = "Packet loss cannot exceed 100%")
    private BigDecimal packetLossPct;

    @Min(value = 0, message = "Throughput must be non-negative")
    @Max(value = 10000000, message = "Throughput exceeds maximum allowed value")
    private BigDecimal throughputMbps;

    public TelemetryRequest() {}

    public TelemetryRequest(Integer latencyMs, BigDecimal packetLossPct, BigDecimal throughputMbps) {
        this.latencyMs = latencyMs;
        this.packetLossPct = packetLossPct;
        this.throughputMbps = throughputMbps;
    }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public BigDecimal getPacketLossPct() { return packetLossPct; }
    public void setPacketLossPct(BigDecimal packetLossPct) { this.packetLossPct = packetLossPct; }
    public BigDecimal getThroughputMbps() { return throughputMbps; }
    public void setThroughputMbps(BigDecimal throughputMbps) { this.throughputMbps = throughputMbps; }
}
