package com.netpath.repository;

import com.netpath.entity.TrafficShiftLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrafficShiftLogRepository extends JpaRepository<TrafficShiftLog, Long> {

    List<TrafficShiftLog> findByPathIdOrderByShiftedAtDesc(Long pathId);
    List<TrafficShiftLog> findAllByOrderByShiftedAtDesc();
}
