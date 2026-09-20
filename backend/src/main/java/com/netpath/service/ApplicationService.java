package com.netpath.service;

import com.netpath.dto.ApplicationDto;
import com.netpath.entity.Application;
import com.netpath.entity.ApplicationStatus;
import com.netpath.entity.User;
import com.netpath.exception.BadRequestException;
import com.netpath.exception.ResourceNotFoundException;
import com.netpath.repository.ApplicationRepository;
import com.netpath.repository.EndpointRepository;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final EndpointRepository endpointRepository;
    private final NetworkPathRepository networkPathRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                              UserRepository userRepository,
                              EndpointRepository endpointRepository,
                              NetworkPathRepository networkPathRepository) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.endpointRepository = endpointRepository;
        this.networkPathRepository = networkPathRepository;
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> getAllApplications() {
        return applicationRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicationDto getApplicationById(Long id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return toDto(app);
    }

    @Transactional(readOnly = true)
    public ApplicationDto getApplicationWithDetails(Long id) {
        Application app = applicationRepository.findByIdWithPaths(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return toDto(app);
    }

    @Transactional
    public ApplicationDto createApplication(ApplicationDto dto, String ownerEmail) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("Application name is required");
        }

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerEmail));

        Application app = new Application(dto.getName(), dto.getDescription(), owner);
        if (dto.getStatus() != null) {
            try {
                app.setStatus(ApplicationStatus.valueOf(dto.getStatus()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + dto.getStatus());
            }
        }

        Application saved = applicationRepository.save(app);
        logger.info("Created application: {} (id={})", saved.getName(), saved.getId());
        return toDto(saved);
    }

    @Transactional
    public ApplicationDto updateApplication(Long id, ApplicationDto dto) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            app.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            app.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            try {
                app.setStatus(ApplicationStatus.valueOf(dto.getStatus()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + dto.getStatus());
            }
        }

        Application saved = applicationRepository.save(app);
        logger.info("Updated application: {} (id={})", saved.getName(), saved.getId());
        return toDto(saved);
    }

    /**
     * Deletes an application and everything under it.
     *
     * <p>The order is deliberate. Letting the ORM cascade the whole tree in one go makes Hibernate
     * load each path, blank the endpoint columns of the path it is about to delete, and flush that
     * dirty row before any delete is issued — which cannot succeed, because both endpoint columns are
     * {@code NOT NULL}. Removing the paths first (the database then clears their telemetry and the
     * recommendations and shift records that name them) leaves the endpoint delete with nothing to
     * point at, so every remaining step is a plain delete.
     */
    @Transactional
    public void deleteApplication(Long id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

        networkPathRepository.deleteByApplicationId(id);
        endpointRepository.deleteByApplicationId(id);
        applicationRepository.delete(app);

        logger.info("Deleted application: id={}", id);
    }

    private ApplicationDto toDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setName(app.getName());
        dto.setDescription(app.getDescription());
        dto.setOwnerId(app.getOwner() != null ? app.getOwner().getId() : null);
        dto.setStatus(app.getStatus() != null ? app.getStatus().name() : null);
        dto.setEndpointCount(endpointRepository.countByApplicationId(app.getId()));
        dto.setPathCount(networkPathRepository.countByApplicationId(app.getId()));
        dto.setCreatedAt(app.getCreatedAt() != null ? DATE_FORMATTER.format(app.getCreatedAt()) : null);
        dto.setUpdatedAt(app.getUpdatedAt() != null ? DATE_FORMATTER.format(app.getUpdatedAt()) : null);
        return dto;
    }
}
