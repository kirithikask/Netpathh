package com.netpath.repository;

import com.netpath.entity.Endpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    Page<Endpoint> findByApplicationId(Long applicationId, Pageable pageable);
    Page<Endpoint> findByRegion(String region, Pageable pageable);

    @Query("SELECT e FROM Endpoint e WHERE e.application.id = :appId AND e.region = :region")
    Page<Endpoint> findByApplicationIdAndRegion(@Param("appId") Long appId,
                                                @Param("region") String region,
                                                Pageable pageable);

    @Query("SELECT DISTINCT e.region FROM Endpoint e ORDER BY e.region")
    List<String> findAllRegions();

    long countByApplicationId(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
