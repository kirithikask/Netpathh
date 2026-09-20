package com.netpath.repository;

import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkPathRepository extends JpaRepository<NetworkPath, Long> {

    Page<NetworkPath> findByApplicationId(Long applicationId, Pageable pageable);
    Page<NetworkPath> findByStatus(PathStatus status, Pageable pageable);

    /**
     * Loads a path with only the associations its DTO needs. Telemetry is deliberately not fetched:
     * a path can have thousands of samples and callers read the aggregate instead.
     */
    @Query("SELECT np FROM NetworkPath np " +
           "LEFT JOIN FETCH np.application " +
           "LEFT JOIN FETCH np.sourceEndpoint " +
           "LEFT JOIN FETCH np.destinationEndpoint " +
           "WHERE np.id = :id")
    Optional<NetworkPath> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT np FROM NetworkPath np " +
           "LEFT JOIN FETCH np.sourceEndpoint " +
           "LEFT JOIN FETCH np.destinationEndpoint " +
           "WHERE np.sourceEndpoint.id = :sourceId AND np.destinationEndpoint.id = :destId " +
           "AND np.isPrimary = true")
    Optional<NetworkPath> findPrimaryPathBetweenEndpoints(@Param("sourceId") Long sourceId,
                                                          @Param("destId") Long destId);

    /** Candidate paths for a recommendation: same origin and destination, excluding the path in use. */
    @Query("SELECT np FROM NetworkPath np " +
           "LEFT JOIN FETCH np.sourceEndpoint " +
           "LEFT JOIN FETCH np.destinationEndpoint " +
           "WHERE np.sourceEndpoint.id = :sourceId AND np.destinationEndpoint.id = :destId " +
           "AND np.id != :excludeId")
    List<NetworkPath> findAlternativePaths(@Param("sourceId") Long sourceId,
                                           @Param("destId") Long destId,
                                           @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(np) FROM NetworkPath np WHERE np.status = :status")
    long countByStatus(@Param("status") PathStatus status);

    long countByApplicationId(Long applicationId);

    /** Tears down an application's paths; their telemetry and references follow in the database. */
    void deleteByApplicationId(Long applicationId);
}
