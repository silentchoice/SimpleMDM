package com.simplemdm.model.mdm;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_child_type", uniqueConstraints = {
    @UniqueConstraint(name = "uk_child_type_code", columnNames = {"object_type_id", "code"}),
    @UniqueConstraint(name = "uk_child_type_system_id", columnNames = {"system_id", "id"})
})
public class ChildType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "object_type_id", nullable = false) private Long objectTypeId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
        @JoinColumn(name = "system_id", referencedColumnName = "system_id", insertable = false, updatable = false),
        @JoinColumn(name = "object_type_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_child_type_object_system"))
    private ObjectType objectType;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(length = 512) private String description;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Version @Column(nullable = false) private Long version;

    protected ChildType() { }
    private ChildType(Long objectTypeId, ObjectType objectType, String code, String name) {
        this.systemId = objectType.getSystemId(); this.objectTypeId = objectTypeId; this.objectType = objectType;
        this.code = code; this.name = name; this.status = "active";
    }
    public static ChildType create(Long objectTypeId, ObjectType objectType, String code, String name) {
        return new ChildType(objectTypeId, objectType, code, name);
    }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getObjectTypeId() { return objectTypeId; }
}
