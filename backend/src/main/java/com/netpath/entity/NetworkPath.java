package com.netpath.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "network_paths",
    uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "source_endpoint_id", "destination_endpoint_id", "path_name"}))
public class NetworkPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    // A path is nothing without both of its ends, so V1 declares these foreign keys ON DELETE CASCADE.
    // Saying so here as well matters twice over: the demo profile builds its schema from these
    // mappings, and without the annotation Hibernate "helpfully" tries to null the column of a
    // deleted endpoint instead of letting the database remove the path, which fails on a NOT NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_endpoint_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Endpoint sourceEndpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_endpoint_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Endpoint destinationEndpoint;

    @Column(name = "path_name", nullable = false)
    private String pathName;

    @Column(nullable = false)
    private Integer hops = 1;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PathStatus status = PathStatus.UNKNOWN;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "path", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PathMetric> metrics = new ArrayList<>();

    public NetworkPath() {}

    public NetworkPath(Application application, Endpoint sourceEndpoint, Endpoint destinationEndpoint,
                       String pathName, Integer hops, String description) {
        this.application = application;
        this.sourceEndpoint = sourceEndpoint;
        this.destinationEndpoint = destinationEndpoint;
        this.pathName = pathName;
        this.hops = hops != null ? hops : 1;
        this.description = description;
        this.status = PathStatus.UNKNOWN;
        this.isPrimary = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }
    public Endpoint getSourceEndpoint() { return sourceEndpoint; }
    public void setSourceEndpoint(Endpoint sourceEndpoint) { this.sourceEndpoint = sourceEndpoint; }
    public Endpoint getDestinationEndpoint() { return destinationEndpoint; }
    public void setDestinationEndpoint(Endpoint destinationEndpoint) { this.destinationEndpoint = destinationEndpoint; }
    public String getPathName() { return pathName; }
    public void setPathName(String pathName) { this.pathName = pathName; }
    public Integer getHops() { return hops; }
    public void setHops(Integer hops) { this.hops = hops; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public PathStatus getStatus() { return status; }
    public void setStatus(PathStatus status) { this.status = status; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<PathMetric> getMetrics() { return metrics; }
    public void setMetrics(List<PathMetric> metrics) { this.metrics = metrics; }
}
