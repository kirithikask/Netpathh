package com.netpath.service;

import com.netpath.dto.NetworkPathDto;
import com.netpath.dto.PathHealthResponseDto;
import com.netpath.dto.PathTelemetrySummary;
import com.netpath.entity.Application;
import com.netpath.entity.Endpoint;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import com.netpath.exception.BadRequestException;
import com.netpath.exception.ResourceNotFoundException;
import com.netpath.repository.ApplicationRepository;
import com.netpath.repository.EndpointRepository;
import com.netpath.repository.NetworkPathRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Owns the path resource: CRUD, and composing a path into the shape clients read.
 *
 * <p>It does not classify health ({@link PathHealthService}) and does not ingest telemetry
 * ({@link TelemetryService}). Lists read the stored status projection; a single path is rendered
 * from the Redis hot state so it agrees with the health endpoint.
 */
@Service
public class NetworkPathService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkPathService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final NetworkPathRepository networkPathRepository;
    private final ApplicationRepository applicationRepository;
    private final EndpointRepository endpointRepository;
    private final PathHealthService pathHealthService;
    private final PathHealthCacheService pathHealthCacheService;

    public NetworkPathService(NetworkPathRepository networkPathRepository,
                              ApplicationRepository applicationRepository,
                              EndpointRepository endpointRepository,
                              PathHealthService pathHealthService,
                              PathHealthCacheService pathHealthCacheService) {
        this.networkPathRepository = networkPathRepository;
        this.applicationRepository = applicationRepository;
        this.endpointRepository = endpointRepository;
        this.pathHealthService = pathHealthService;
        this.pathHealthCacheService = pathHealthCacheService;
    }

    @Transactional(readOnly = true)
    public Page<NetworkPathDto> getAllPaths(Pageable pageable) {
        return toDtoPage(networkPathRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Page<NetworkPathDto> getPathsByApplication(Long applicationId, Pageable pageable) {
        requireApplication(applicationId);
        return toDtoPage(networkPathRepository.findByApplicationId(applicationId, pageable));
    }

    @Transactional(readOnly = true)
    public Page<NetworkPathDto> getPathsByStatus(PathStatus status, Pageable pageable) {
        return toDtoPage(networkPathRepository.findByStatus(status, pageable));
    }

    @Transactional(readOnly = true)
    public NetworkPathDto getPathById(Long id) {
        return toLiveDto(requirePath(id));
    }

    @Transactional(readOnly = true)
    public PathHealthResponseDto getHealth(Long pathId) {
        requirePath(pathId);
        return pathHealthCacheService.get(pathId);
    }

    @Transactional
    public NetworkPathDto createPath(NetworkPathDto dto, Long applicationId) {
        Application application = requireApplication(applicationId);

        Endpoint source = requireEndpoint(dto.getSourceEndpointId(), "Source");
        Endpoint destination = requireEndpoint(dto.getDestinationEndpointId(), "Destination");

        if (source.getId().equals(destination.getId())) {
            throw new BadRequestException("Source and destination endpoints must be different");
        }

        boolean primary = dto.getIsPrimary() == null || dto.getIsPrimary();
        if (primary && networkPathRepository
                .findPrimaryPathBetweenEndpoints(source.getId(), destination.getId())
                .isPresent()) {
            throw new BadRequestException("A primary path already exists between these endpoints");
        }

        NetworkPath path = new NetworkPath(
                application, source, destination, dto.getPathName(), dto.getHops(), dto.getDescription());
        path.setIsPrimary(primary);

        NetworkPath saved = networkPathRepository.save(path);
        logger.info("Created path {} ({} -> {}) in application {}",
                saved.getPathName(), source.getName(), destination.getName(), applicationId);

        return toLiveDto(saved);
    }

    @Transactional
    public NetworkPathDto updatePath(Long id, NetworkPathDto dto) {
        NetworkPath path = requirePath(id);

        if (dto.getPathName() != null && !dto.getPathName().isBlank()) {
            path.setPathName(dto.getPathName());
        }
        if (dto.getDescription() != null) {
            path.setDescription(dto.getDescription());
        }
        if (dto.getHops() != null && dto.getHops() >= 1) {
            path.setHops(dto.getHops());
        }
        if (dto.getIsPrimary() != null) {
            path.setIsPrimary(dto.getIsPrimary());
        }

        NetworkPath saved = networkPathRepository.save(path);
        logger.info("Updated path {} (id={})", saved.getPathName(), saved.getId());
        return toLiveDto(saved);
    }

    @Transactional
    public void deletePath(Long id) {
        requirePath(id);
        networkPathRepository.deleteById(id);
        pathHealthCacheService.invalidate(id);
        logger.info("Deleted path id={}", id);
    }

    private Page<NetworkPathDto> toDtoPage(Page<NetworkPath> page) {
        List<NetworkPath> paths = page.getContent();

        // One aggregate query for the whole page instead of one per path.
        Map<Long, PathTelemetrySummary> summaries = pathHealthService.summarise(
                paths.stream().map(NetworkPath::getId).collect(Collectors.toList()));

        List<NetworkPathDto> content = paths.stream()
                .map(path -> applySummary(toDto(path), summaries.get(path.getId())))
                .collect(Collectors.toList());

        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /** Single-path view, rendered from the same hot state the health endpoint serves. */
    private NetworkPathDto toLiveDto(NetworkPath path) {
        NetworkPathDto dto = toDto(path);
        PathHealthResponseDto health = pathHealthCacheService.get(path.getId());

        if (health == null) {
            return dto;
        }

        dto.setStatus(health.getStatus());
        dto.setAverageLatencyMs(health.getAverageLatencyMs());
        dto.setAveragePacketLossPct(health.getAveragePacketLossPct());
        dto.setMetricsCount(health.getMetricsCount());
        dto.setLastUpdated(health.getLastUpdated());
        return dto;
    }

    private NetworkPathDto toDto(NetworkPath path) {
        NetworkPathDto dto = new NetworkPathDto();
        dto.setId(path.getId());
        dto.setPathName(path.getPathName());
        dto.setDescription(path.getDescription());
        dto.setHops(path.getHops());
        dto.setIsPrimary(path.getIsPrimary());
        dto.setStatus(path.getStatus() != null ? path.getStatus().name() : PathStatus.UNKNOWN.name());

        if (path.getApplication() != null) {
            dto.setApplicationId(path.getApplication().getId());
            dto.setApplicationName(path.getApplication().getName());
        }

        Endpoint source = path.getSourceEndpoint();
        if (source != null) {
            dto.setSourceEndpointId(source.getId());
            dto.setSourceEndpointName(source.getName());
            dto.setSourceEndpointIp(source.getIpAddress());
            dto.setSourceEndpointRegion(source.getRegion());
        }

        Endpoint destination = path.getDestinationEndpoint();
        if (destination != null) {
            dto.setDestinationEndpointId(destination.getId());
            dto.setDestinationEndpointName(destination.getName());
            dto.setDestinationEndpointIp(destination.getIpAddress());
            dto.setDestinationEndpointRegion(destination.getRegion());
        }

        return dto;
    }

    private NetworkPathDto applySummary(NetworkPathDto dto, PathTelemetrySummary summary) {
        if (summary == null) {
            dto.setMetricsCount(0L);
            return dto;
        }

        dto.setAverageLatencyMs(summary.averageLatencyMs());
        dto.setAveragePacketLossPct(summary.averagePacketLossPct());
        dto.setMetricsCount(summary.sampleCount());
        dto.setLastUpdated(summary.lastTimestamp() != null
                ? DATE_FORMATTER.format(summary.lastTimestamp())
                : null);
        return dto;
    }

    private NetworkPath requirePath(Long id) {
        return networkPathRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("NetworkPath not found with id: " + id));
    }

    private Application requireApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found with id: " + applicationId));
    }

    private Endpoint requireEndpoint(Long endpointId, String role) {
        return Optional.ofNullable(endpointId)
                .flatMap(endpointRepository::findById)
                .orElseThrow(() -> new ResourceNotFoundException(
                        role + " endpoint not found with id: " + endpointId));
    }
}
