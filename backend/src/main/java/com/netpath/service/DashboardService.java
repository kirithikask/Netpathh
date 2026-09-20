package com.netpath.service;

import com.netpath.dto.DashboardStatsDto;
import com.netpath.entity.PathStatus;
import com.netpath.repository.ApplicationRepository;
import com.netpath.repository.EndpointRepository;
import com.netpath.repository.NetworkPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final EndpointRepository endpointRepository;
    private final NetworkPathRepository networkPathRepository;

    public DashboardService(ApplicationRepository applicationRepository,
                            EndpointRepository endpointRepository,
                            NetworkPathRepository networkPathRepository) {
        this.applicationRepository = applicationRepository;
        this.endpointRepository = endpointRepository;
        this.networkPathRepository = networkPathRepository;
    }

    /**
     * Path status counts are read from the stored status column, which telemetry ingestion keeps
     * up to date, so this stays a handful of indexed count queries instead of a health
     * recalculation per path.
     */
    @Transactional(readOnly = true)
    public DashboardStatsDto getStats() {
        return new DashboardStatsDto(
                applicationRepository.count(),
                endpointRepository.count(),
                networkPathRepository.count(),
                networkPathRepository.countByStatus(PathStatus.HEALTHY),
                networkPathRepository.countByStatus(PathStatus.DEGRADED),
                networkPathRepository.countByStatus(PathStatus.DOWN),
                networkPathRepository.countByStatus(PathStatus.UNKNOWN)
        );
    }
}
