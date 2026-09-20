package com.netpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public class NetworkPathDto {

    private Long id;

    @NotBlank(message = "Path name is required")
    private String pathName;

    private String description;

    @NotNull(message = "Source endpoint is required")
    private Long sourceEndpointId;

    @NotNull(message = "Destination endpoint is required")
    private Long destinationEndpointId;

    @NotNull(message = "Application is required")
    private Long applicationId;

    @Min(value = 1, message = "Hops must be at least 1")
    private Integer hops = 1;

    private Boolean isPrimary = true;

    private String sourceEndpointName;
    private String sourceEndpointIp;
    private String sourceEndpointRegion;
    private String destinationEndpointName;
    private String destinationEndpointIp;
    private String destinationEndpointRegion;
    private String applicationName;
    private String status;
    private Double averageLatencyMs;
    private Double averagePacketLossPct;
    private Long metricsCount;
    private String lastUpdated;

    public NetworkPathDto() {}

    public NetworkPathDto(String pathName, Long sourceEndpointId, Long destinationEndpointId,
                          Long applicationId) {
        this.pathName = pathName;
        this.sourceEndpointId = sourceEndpointId;
        this.destinationEndpointId = destinationEndpointId;
        this.applicationId = applicationId;
        this.hops = 1;
        this.isPrimary = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPathName() { return pathName; }
    public void setPathName(String pathName) { this.pathName = pathName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getSourceEndpointId() { return sourceEndpointId; }
    public void setSourceEndpointId(Long sourceEndpointId) { this.sourceEndpointId = sourceEndpointId; }
    public Long getDestinationEndpointId() { return destinationEndpointId; }
    public void setDestinationEndpointId(Long destinationEndpointId) { this.destinationEndpointId = destinationEndpointId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Integer getHops() { return hops; }
    public void setHops(Integer hops) { this.hops = hops; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
    public String getSourceEndpointName() { return sourceEndpointName; }
    public void setSourceEndpointName(String sourceEndpointName) { this.sourceEndpointName = sourceEndpointName; }
    public String getSourceEndpointIp() { return sourceEndpointIp; }
    public void setSourceEndpointIp(String sourceEndpointIp) { this.sourceEndpointIp = sourceEndpointIp; }
    public String getSourceEndpointRegion() { return sourceEndpointRegion; }
    public void setSourceEndpointRegion(String sourceEndpointRegion) { this.sourceEndpointRegion = sourceEndpointRegion; }
    public String getDestinationEndpointName() { return destinationEndpointName; }
    public void setDestinationEndpointName(String destinationEndpointName) { this.destinationEndpointName = destinationEndpointName; }
    public String getDestinationEndpointIp() { return destinationEndpointIp; }
    public void setDestinationEndpointIp(String destinationEndpointIp) { this.destinationEndpointIp = destinationEndpointIp; }
    public String getDestinationEndpointRegion() { return destinationEndpointRegion; }
    public void setDestinationEndpointRegion(String destinationEndpointRegion) { this.destinationEndpointRegion = destinationEndpointRegion; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getAverageLatencyMs() { return averageLatencyMs; }
    public void setAverageLatencyMs(Double averageLatencyMs) { this.averageLatencyMs = averageLatencyMs; }
    public Double getAveragePacketLossPct() { return averagePacketLossPct; }
    public void setAveragePacketLossPct(Double averagePacketLossPct) { this.averagePacketLossPct = averagePacketLossPct; }
    public Long getMetricsCount() { return metricsCount; }
    public void setMetricsCount(Long metricsCount) { this.metricsCount = metricsCount; }
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
}
