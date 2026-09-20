package com.netpath.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "traffic_shift_logs")
public class TrafficShiftLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A shift record outlives neither path it names; the log line itself must survive. Matches the
    // foreign keys declared in V1__initial_schema.sql for deployments that let Hibernate build it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "path_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NetworkPath path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_path_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private NetworkPath oldPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_path_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private NetworkPath newPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status = ShiftStatus.SIMULATED;

    @Column(name = "shifted_at", nullable = false)
    private Instant shiftedAt;

    public TrafficShiftLog() {}

    public TrafficShiftLog(NetworkPath path, NetworkPath oldPath, NetworkPath newPath,
                           String reason, ShiftStatus status) {
        this.path = path;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.reason = reason;
        this.status = status != null ? status : ShiftStatus.SIMULATED;
        this.shiftedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        shiftedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NetworkPath getPath() { return path; }
    public void setPath(NetworkPath path) { this.path = path; }
    public NetworkPath getOldPath() { return oldPath; }
    public void setOldPath(NetworkPath oldPath) { this.oldPath = oldPath; }
    public NetworkPath getNewPath() { return newPath; }
    public void setNewPath(NetworkPath newPath) { this.newPath = newPath; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public ShiftStatus getStatus() { return status; }
    public void setStatus(ShiftStatus status) { this.status = status; }
    public Instant getShiftedAt() { return shiftedAt; }
    public void setShiftedAt(Instant shiftedAt) { this.shiftedAt = shiftedAt; }
}
