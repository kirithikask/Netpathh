package com.netpath.dto;

public class TrafficShiftResponse {

    private Long id;
    private Long pathId;
    private Long oldPathId;
    private String oldPathName;
    private Long newPathId;
    private String newPathName;
    private String reason;
    private String status;
    private String shiftedAt;

    public TrafficShiftResponse() {}

    public TrafficShiftResponse(Long id, Long pathId, Long oldPathId, String oldPathName,
                                Long newPathId, String newPathName, String reason,
                                String status, String shiftedAt) {
        this.id = id;
        this.pathId = pathId;
        this.oldPathId = oldPathId;
        this.oldPathName = oldPathName;
        this.newPathId = newPathId;
        this.newPathName = newPathName;
        this.reason = reason;
        this.status = status;
        this.shiftedAt = shiftedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPathId() { return pathId; }
    public void setPathId(Long pathId) { this.pathId = pathId; }
    public Long getOldPathId() { return oldPathId; }
    public void setOldPathId(Long oldPathId) { this.oldPathId = oldPathId; }
    public String getOldPathName() { return oldPathName; }
    public void setOldPathName(String oldPathName) { this.oldPathName = oldPathName; }
    public Long getNewPathId() { return newPathId; }
    public void setNewPathId(Long newPathId) { this.newPathId = newPathId; }
    public String getNewPathName() { return newPathName; }
    public void setNewPathName(String newPathName) { this.newPathName = newPathName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getShiftedAt() { return shiftedAt; }
    public void setShiftedAt(String shiftedAt) { this.shiftedAt = shiftedAt; }
}
