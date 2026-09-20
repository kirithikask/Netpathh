package com.netpath.dto;

import jakarta.validation.constraints.NotBlank;

public class TrafficShiftRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
    private Long recommendedPathId;

    public TrafficShiftRequest() {}

    public TrafficShiftRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getRecommendedPathId() { return recommendedPathId; }
    public void setRecommendedPathId(Long recommendedPathId) { this.recommendedPathId = recommendedPathId; }
}
