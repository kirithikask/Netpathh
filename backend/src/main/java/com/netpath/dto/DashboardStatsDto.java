package com.netpath.dto;

public class DashboardStatsDto {

    private Long totalApplications;
    private Long totalEndpoints;
    private Long totalPaths;
    private Long healthyPaths;
    private Long degradedPaths;
    private Long downPaths;
    private Long unknownPaths;

    public DashboardStatsDto() {}

    public DashboardStatsDto(Long totalApplications, Long totalEndpoints, Long totalPaths,
                             Long healthyPaths, Long degradedPaths, Long downPaths,
                             Long unknownPaths) {
        this.totalApplications = totalApplications;
        this.totalEndpoints = totalEndpoints;
        this.totalPaths = totalPaths;
        this.healthyPaths = healthyPaths;
        this.degradedPaths = degradedPaths;
        this.downPaths = downPaths;
        this.unknownPaths = unknownPaths;
    }

    public Long getTotalApplications() { return totalApplications; }
    public void setTotalApplications(Long totalApplications) { this.totalApplications = totalApplications; }
    public Long getTotalEndpoints() { return totalEndpoints; }
    public void setTotalEndpoints(Long totalEndpoints) { this.totalEndpoints = totalEndpoints; }
    public Long getTotalPaths() { return totalPaths; }
    public void setTotalPaths(Long totalPaths) { this.totalPaths = totalPaths; }
    public Long getHealthyPaths() { return healthyPaths; }
    public void setHealthyPaths(Long healthyPaths) { this.healthyPaths = healthyPaths; }
    public Long getDegradedPaths() { return degradedPaths; }
    public void setDegradedPaths(Long degradedPaths) { this.degradedPaths = degradedPaths; }
    public Long getDownPaths() { return downPaths; }
    public void setDownPaths(Long downPaths) { this.downPaths = downPaths; }
    public Long getUnknownPaths() { return unknownPaths; }
    public void setUnknownPaths(Long unknownPaths) { this.unknownPaths = unknownPaths; }
}
