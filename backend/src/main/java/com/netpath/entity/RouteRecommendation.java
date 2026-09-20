package com.netpath.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "route_recommendations")
public class RouteRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting a path must not fail because a recommendation mentioned it. The dev profile builds
    // its schema from these mappings, so the rule is repeated here to match V1__initial_schema.sql.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "path_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NetworkPath path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_path_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private NetworkPath recommendedPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_endpoint_id", nullable = false)
    private Long sourceEndpointId;

    @Column(name = "destination_endpoint_id", nullable = false)
    private Long destinationEndpointId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PathStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RouteRecommendation() {}

    public RouteRecommendation(NetworkPath path, NetworkPath recommendedPath, String reason,
                               Long sourceEndpointId, Long destinationEndpointId, PathStatus status) {
        this.path = path;
        this.recommendedPath = recommendedPath;
        this.reason = reason;
        this.sourceEndpointId = sourceEndpointId;
        this.destinationEndpointId = destinationEndpointId;
        this.status = status;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NetworkPath getPath() { return path; }
    public void setPath(NetworkPath path) { this.path = path; }
    public NetworkPath getRecommendedPath() { return recommendedPath; }
    public void setRecommendedPath(NetworkPath recommendedPath) { this.recommendedPath = recommendedPath; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getSourceEndpointId() { return sourceEndpointId; }
    public void setSourceEndpointId(Long sourceEndpointId) { this.sourceEndpointId = sourceEndpointId; }
    public Long getDestinationEndpointId() { return destinationEndpointId; }
    public void setDestinationEndpointId(Long destinationEndpointId) { this.destinationEndpointId = destinationEndpointId; }
    public PathStatus getStatus() { return status; }
    public void setStatus(PathStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
