package com.netpath.dto;

public class RouteRecommendationDto {

    private Long currentPathId;
    private String currentPathName;
    private String currentStatus;
    private Double currentAvgLatencyMs;
    private Double currentAvgPacketLossPct;
    private String sourceEndpoint;
    private String destinationEndpoint;
    private Long recommendedPathId;
    private String recommendedPathName;
    private String recommendedStatus;
    private Double recommendedAvgLatencyMs;
    private Double recommendedAvgPacketLossPct;
    private String reason;
    private Boolean hasAlternative;
    private String recommendationTimestamp;

    public Long getCurrentPathId() { return currentPathId; }
    public void setCurrentPathId(Long currentPathId) { this.currentPathId = currentPathId; }
    public String getCurrentPathName() { return currentPathName; }
    public void setCurrentPathName(String currentPathName) { this.currentPathName = currentPathName; }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    public Double getCurrentAvgLatencyMs() { return currentAvgLatencyMs; }
    public void setCurrentAvgLatencyMs(Double currentAvgLatencyMs) { this.currentAvgLatencyMs = currentAvgLatencyMs; }
    public Double getCurrentAvgPacketLossPct() { return currentAvgPacketLossPct; }
    public void setCurrentAvgPacketLossPct(Double currentAvgPacketLossPct) { this.currentAvgPacketLossPct = currentAvgPacketLossPct; }
    public String getSourceEndpoint() { return sourceEndpoint; }
    public void setSourceEndpoint(String sourceEndpoint) { this.sourceEndpoint = sourceEndpoint; }
    public String getDestinationEndpoint() { return destinationEndpoint; }
    public void setDestinationEndpoint(String destinationEndpoint) { this.destinationEndpoint = destinationEndpoint; }
    public Long getRecommendedPathId() { return recommendedPathId; }
    public void setRecommendedPathId(Long recommendedPathId) { this.recommendedPathId = recommendedPathId; }
    public String getRecommendedPathName() { return recommendedPathName; }
    public void setRecommendedPathName(String recommendedPathName) { this.recommendedPathName = recommendedPathName; }
    public String getRecommendedStatus() { return recommendedStatus; }
    public void setRecommendedStatus(String recommendedStatus) { this.recommendedStatus = recommendedStatus; }
    public Double getRecommendedAvgLatencyMs() { return recommendedAvgLatencyMs; }
    public void setRecommendedAvgLatencyMs(Double recommendedAvgLatencyMs) { this.recommendedAvgLatencyMs = recommendedAvgLatencyMs; }
    public Double getRecommendedAvgPacketLossPct() { return recommendedAvgPacketLossPct; }
    public void setRecommendedAvgPacketLossPct(Double recommendedAvgPacketLossPct) { this.recommendedAvgPacketLossPct = recommendedAvgPacketLossPct; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getHasAlternative() { return hasAlternative; }
    public void setHasAlternative(Boolean hasAlternative) { this.hasAlternative = hasAlternative; }
    public String getRecommendationTimestamp() { return recommendationTimestamp; }
    public void setRecommendationTimestamp(String recommendationTimestamp) { this.recommendationTimestamp = recommendationTimestamp; }
}
