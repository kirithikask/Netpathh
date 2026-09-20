package com.netpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EndpointDto {

    private Long id;

    @NotBlank(message = "Endpoint name is required")
    @Size(min = 2, max = 255, message = "Endpoint name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "IP address is required")
    private String ipAddress;

    @NotBlank(message = "Region is required")
    private String region;

    private String status;
    private Long applicationId;
    private String applicationName;
    private String createdAt;
    private String updatedAt;

    public EndpointDto() {}

    public EndpointDto(String name, String ipAddress, String region) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.region = region;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
