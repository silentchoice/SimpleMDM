package com.simplemdm.model.mdm;

import com.simplemdm.model.system.SystemEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_object_type", uniqueConstraints = {
    @UniqueConstraint(name = "uk_object_type_code", columnNames = {"system_id", "code"}),
    @UniqueConstraint(name = "uk_object_type_system_id", columnNames = {"system_id", "id"})
})
public class ObjectType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_object_type_system"))
    private SystemEntity system;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "department_scoped", nullable = false) private boolean departmentScoped;
    @Column(name = "approval_required", nullable = false) private boolean approvalRequired;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Version @Column(nullable = false) private Long version;

    protected ObjectType() { }

    private ObjectType(Long systemId, String code, String name) {
        this.systemId = systemId;
        this.code = code;
        this.name = name;
        this.status = "active";
        this.departmentScoped = true;
        this.approvalRequired = false;
    }

    public static ObjectType create(Long systemId, String code, String name) {
        return new ObjectType(systemId, code, name);
    }

    public static ObjectType create(SystemEntity system, String code, String name) {
        return new ObjectType(system.getId(), code, name);
    }

    public void apply(String name, boolean approvalRequired, boolean departmentScoped, Long actorId) {
        this.name = name;
        this.approvalRequired = approvalRequired;
        this.departmentScoped = departmentScoped;
        this.updatedBy = actorId;
    }

    public void deactivate(Long actorId) {
        this.status = "inactive";
        this.updatedBy = actorId;
    }

    public void reactivate(Long actorId) {
        this.status = "active";
        this.updatedBy = actorId;
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public boolean isDepartmentScoped() { return departmentScoped; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public boolean isActive() { return "active".equals(status); }
}
