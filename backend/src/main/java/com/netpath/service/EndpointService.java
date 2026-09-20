package com.netpath.service;

import com.netpath.dto.EndpointDto;
import com.netpath.entity.Application;
import com.netpath.entity.Endpoint;
import com.netpath.entity.EndpointStatus;
import com.netpath.exception.BadRequestException;
import com.netpath.exception.ResourceNotFoundException;
import com.netpath.repository.ApplicationRepository;
import com.netpath.repository.EndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EndpointService {

    private static final Logger logger = LoggerFactory.getLogger(EndpointService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final EndpointRepository endpointRepository;
    private final ApplicationRepository applicationRepository;

    public EndpointService(EndpointRepository endpointRepository,
                           ApplicationRepository applicationRepository) {
        this.endpointRepository = endpointRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public Page<EndpointDto> getAllEndpoints(Pageable pageable) {
        return endpointRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<EndpointDto> getEndpointsByApplication(Long applicationId, Pageable pageable) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found with id: " + applicationId);
        }
        return endpointRepository.findByApplicationId(applicationId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<EndpointDto> getEndpointsByRegion(String region, Pageable pageable) {
        return endpointRepository.findByRegion(region, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<EndpointDto> getEndpointsByApplicationAndRegion(Long applicationId, String region, Pageable pageable) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found with id: " + applicationId);
        }
        return endpointRepository.findByApplicationIdAndRegion(applicationId, region, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public EndpointDto getEndpointById(Long id) {
        Endpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + id));
        return toDto(endpoint);
    }

    @Transactional
    public EndpointDto createEndpoint(EndpointDto dto, Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("Endpoint name is required");
        }
        if (dto.getIpAddress() == null || dto.getIpAddress().isBlank()) {
            throw new BadRequestException("IP address is required");
        }
        if (dto.getRegion() == null || dto.getRegion().isBlank()) {
            throw new BadRequestException("Region is required");
        }

        Endpoint endpoint = new Endpoint(app, dto.getName(), dto.getIpAddress(), dto.getRegion());
        if (dto.getStatus() != null) {
            try {
                endpoint.setStatus(EndpointStatus.valueOf(dto.getStatus()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + dto.getStatus());
            }
        }

        Endpoint saved = endpointRepository.save(endpoint);
        logger.info("Created endpoint: {} ({}) in application {}", saved.getName(), saved.getIpAddress(), applicationId);
        return toDto(saved);
    }

    @Transactional
    public EndpointDto updateEndpoint(Long id, EndpointDto dto) {
        Endpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + id));

        if (dto.getName() != null && !dto.getName().isBlank()) endpoint.setName(dto.getName());
        if (dto.getIpAddress() != null && !dto.getIpAddress().isBlank()) endpoint.setIpAddress(dto.getIpAddress());
        if (dto.getRegion() != null && !dto.getRegion().isBlank()) endpoint.setRegion(dto.getRegion());
        if (dto.getStatus() != null) {
            try {
                endpoint.setStatus(EndpointStatus.valueOf(dto.getStatus()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + dto.getStatus());
            }
        }

        Endpoint saved = endpointRepository.save(endpoint);
        logger.info("Updated endpoint: {} (id={})", saved.getName(), saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        if (!endpointRepository.existsById(id)) {
            throw new ResourceNotFoundException("Endpoint not found with id: " + id);
        }
        endpointRepository.deleteById(id);
        logger.info("Deleted endpoint: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<String> getAllRegions() {
        return endpointRepository.findAllRegions();
    }

    private EndpointDto toDto(Endpoint endpoint) {
        EndpointDto dto = new EndpointDto();
        dto.setId(endpoint.getId());
        dto.setName(endpoint.getName());
        dto.setIpAddress(endpoint.getIpAddress());
        dto.setRegion(endpoint.getRegion());
        dto.setStatus(endpoint.getStatus() != null ? endpoint.getStatus().name() : null);
        dto.setApplicationId(endpoint.getApplication() != null ? endpoint.getApplication().getId() : null);
        dto.setApplicationName(endpoint.getApplication() != null ? endpoint.getApplication().getName() : null);
        dto.setCreatedAt(endpoint.getCreatedAt() != null ? DATE_FORMATTER.format(endpoint.getCreatedAt()) : null);
        dto.setUpdatedAt(endpoint.getUpdatedAt() != null ? DATE_FORMATTER.format(endpoint.getUpdatedAt()) : null);
        return dto;
    }
}
