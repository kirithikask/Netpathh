package com.netpath.repository;

import com.netpath.entity.RouteRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Recommendations are written as an audit trail. Nothing reads them back yet, so no finder is
 * declared until something needs one.
 */
@Repository
public interface RouteRecommendationRepository extends JpaRepository<RouteRecommendation, Long> {
}
