package com.netpath.dto;

import java.time.Instant;

/**
 * One description of what telemetry says about a path.
 *
 * <p>Classification, path listing and route recommendations all consume this shape, so there is a
 * single definition of "the average latency of a path" and a single place where the JDBC types the
 * aggregate query returns are turned into numbers.
 *
 * <p>Rows arrive as
 * {@code [pathId, avgLatency, avgPacketLoss, maxLatency, maxPacketLoss, sampleCount, lastTimestamp]}.
 */
public record PathTelemetrySummary(
        Long pathId,
        long sampleCount,
        Double averageLatencyMs,
        Double averagePacketLossPct,
        Double maxLatencyMs,
        Double maxPacketLossPct,
        Instant lastTimestamp) {

    public static PathTelemetrySummary empty(Long pathId) {
        return new PathTelemetrySummary(pathId, 0L, null, null, null, null, null);
    }

    public static PathTelemetrySummary fromRow(Object[] row) {
        return new PathTelemetrySummary(
                ((Number) row[0]).longValue(),
                row[5] != null ? ((Number) row[5]).longValue() : 0L,
                asDouble(row[1]),
                asDouble(row[2]),
                asDouble(row[3]),
                asDouble(row[4]),
                asInstant(row[6]));
    }

    public boolean hasEnoughSamples(int minimum) {
        return sampleCount >= minimum;
    }

    private static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Instant asInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }
}
