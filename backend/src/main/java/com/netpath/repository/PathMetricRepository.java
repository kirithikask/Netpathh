package com.netpath.repository;

import com.netpath.entity.PathMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface PathMetricRepository extends JpaRepository<PathMetric, Long> {

    Page<PathMetric> findByPathIdOrderByTimestampDesc(Long pathId, Pageable pageable);

    @Query("SELECT pm FROM PathMetric pm WHERE pm.path.id = :pathId " +
           "AND pm.timestamp >= :from AND pm.timestamp <= :to " +
           "ORDER BY pm.timestamp DESC")
    Page<PathMetric> findByPathIdAndTimestampBetween(@Param("pathId") Long pathId,
                                                     @Param("from") Instant from,
                                                     @Param("to") Instant to,
                                                     Pageable pageable);

    @Query("SELECT pm FROM PathMetric pm WHERE pm.path.id = :pathId " +
           "ORDER BY pm.timestamp DESC")
    List<PathMetric> findRecentByPathId(@Param("pathId") Long pathId, Pageable pageable);

    /**
     * The only aggregate query in the application. Accepts a batch of paths so listing a page of
     * paths costs one round trip, and a single-element batch when health needs to be evaluated.
     * Rows are consumed by {@code PathTelemetrySummary.fromRow}.
     *
     * <p>{@code since} bounds the aggregation to the current-health window. A path with no recent
     * samples simply produces no row, which the caller reads as UNKNOWN rather than as "still
     * healthy". Historical samples stay in the table; they are just not current health.
     */
    @Query("SELECT pm.path.id, AVG(pm.latencyMs), AVG(pm.packetLossPct), MAX(pm.latencyMs), " +
           "MAX(pm.packetLossPct), COUNT(pm), MAX(pm.timestamp) " +
           "FROM PathMetric pm WHERE pm.path.id IN :pathIds AND pm.timestamp >= :since " +
           "GROUP BY pm.path.id")
    List<Object[]> summariseForPaths(@Param("pathIds") Collection<Long> pathIds,
                                     @Param("since") Instant since);
}
