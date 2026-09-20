package com.netpath.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "path_metrics")
public class PathMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Telemetry belongs to its path and goes with it, whichever route removes the path: directly, or
    // as a consequence of deleting one of the endpoints. Matches path_metrics in V1.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "path_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NetworkPath path;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "packet_loss_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal packetLossPct;

    @Column(name = "throughput_mbps", precision = 10, scale = 2)
    private BigDecimal throughputMbps;

    @Column(nullable = false)
    private Instant timestamp;

    public PathMetric() {}

    public PathMetric(NetworkPath path, Integer latencyMs, BigDecimal packetLossPct,
                      BigDecimal throughputMbps) {
        this.path = path;
        this.latencyMs = latencyMs;
        this.packetLossPct = packetLossPct;
        this.throughputMbps = throughputMbps;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NetworkPath getPath() { return path; }
    public void setPath(NetworkPath path) { this.path = path; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public BigDecimal getPacketLossPct() { return packetLossPct; }
    public void setPacketLossPct(BigDecimal packetLossPct) { this.packetLossPct = packetLossPct; }
    public BigDecimal getThroughputMbps() { return throughputMbps; }
    public void setThroughputMbps(BigDecimal throughputMbps) { this.throughputMbps = throughputMbps; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
