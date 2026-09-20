package com.netpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ApplicationDto {

    private Long id;

    @NotBlank(message = "Application name is required")
    @Size(min = 2, max = 255, message = "Application name must be between 2 and 255 characters")
    private String name;

    private String description;
    private Long ownerId;
    private String status;
    private Long endpointCount;
    private Long pathCount;
    private String createdAt;
    private String updatedAt;

    public ApplicationDto() {}

    public ApplicationDto(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getEndpointCount() { return endpointCount; }
    public void setEndpointCount(Long endpointCount) { this.endpointCount = endpointCount; }
    public Long getPathCount() { return pathCount; }
    public void setPathCount(Long pathCount) { this.pathCount = pathCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
