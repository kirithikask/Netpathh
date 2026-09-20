package com.netpath.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Classification boundaries, one pair per metric.
 *
 * <p>A path is DEGRADED at or above the {@code degraded-*} boundary and DOWN at or above the
 * {@code down-*} boundary. There is no third "healthy ceiling" pair: with two outcomes above
 * HEALTHY, a middle threshold could never change a result, so it does not exist.
 *
 * <p>{@code window-minutes} bounds which samples those boundaries are applied to. Aggregating the
 * whole telemetry table would let one bad hour from last month hold a now-healthy path DEGRADED
 * indefinitely, and a path that stopped reporting would keep its last verdict forever.
 */
@Component
@ConfigurationProperties(prefix = "app.path-health")
public class PathHealthProperties {

    private Thresholds thresholds = new Thresholds();
    private Cache cache = new Cache();

    /**
     * How far back current health looks. Only samples newer than this describe the path as it is
     * now; anything older is history and must not keep a path DEGRADED (or HEALTHY) forever.
     */
    private int windowMinutes = 60;

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public static class Thresholds {

        private double degradedLatencyMs = 100;
        private double downLatencyMs = 500;
        private double degradedPacketLossPct = 0.5;
        private double downPacketLossPct = 10.0;
        private int minMetricsForEvaluation = 3;

        public double getDegradedLatencyMs() { return degradedLatencyMs; }
        public void setDegradedLatencyMs(double degradedLatencyMs) { this.degradedLatencyMs = degradedLatencyMs; }
        public double getDownLatencyMs() { return downLatencyMs; }
        public void setDownLatencyMs(double downLatencyMs) { this.downLatencyMs = downLatencyMs; }
        public double getDegradedPacketLossPct() { return degradedPacketLossPct; }
        public void setDegradedPacketLossPct(double degradedPacketLossPct) { this.degradedPacketLossPct = degradedPacketLossPct; }
        public double getDownPacketLossPct() { return downPacketLossPct; }
        public void setDownPacketLossPct(double downPacketLossPct) { this.downPacketLossPct = downPacketLossPct; }
        public int getMinMetricsForEvaluation() { return minMetricsForEvaluation; }
        public void setMinMetricsForEvaluation(int minMetricsForEvaluation) { this.minMetricsForEvaluation = minMetricsForEvaluation; }
    }

    public static class Cache {

        private int ttlSeconds = 120;

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }
}
