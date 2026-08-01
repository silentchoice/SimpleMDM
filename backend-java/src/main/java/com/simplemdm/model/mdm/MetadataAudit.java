package com.simplemdm.model.mdm;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_metadata_audit", uniqueConstraints =
    @UniqueConstraint(name = "uk_metadata_audit_system_id", columnNames = {"system_id", "id"}))
public class MetadataAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(name = "entity_type", nullable = false, length = 32) private String entityType;
    @Column(name = "entity_id", nullable = false) private Long entityId;
    @Column(nullable = false, length = 32) private String action;
    @Column(name = "before_snapshot", columnDefinition = "TEXT") private String beforeSnapshot;
    @Column(name = "after_snapshot", columnDefinition = "TEXT") private String afterSnapshot;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected MetadataAudit() { }
    private MetadataAudit(Long systemId, Long actorId, String entityType, Long entityId, String action,
                          String beforeSnapshot, String afterSnapshot) {
        this.systemId = systemId; this.actorId = actorId; this.entityType = entityType; this.entityId = entityId;
        this.action = action; this.beforeSnapshot = beforeSnapshot; this.afterSnapshot = afterSnapshot;
    }
    public static MetadataAudit create(Long systemId, Long actorId, String entityType, Long entityId, String action,
                                       String beforeSnapshot, String afterSnapshot) {
        return new MetadataAudit(systemId, actorId, entityType, entityId, action, beforeSnapshot, afterSnapshot);
    }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getBeforeSnapshot() { return beforeSnapshot; }
    public String getAfterSnapshot() { return afterSnapshot; }
}
