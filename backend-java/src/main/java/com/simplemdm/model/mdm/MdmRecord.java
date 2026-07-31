package com.simplemdm.model.mdm;

import com.simplemdm.model.system.Department;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_record")
public class MdmRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "object_type_id", nullable = false) private Long objectTypeId;
    @Column(name = "department_id", nullable = false) private Long departmentId;
    @Column(name = "record_code", nullable = false, length = 128) private String recordCode;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "approval_status", nullable = false, length = 32) private String approvalStatus;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    protected MdmRecord() { }
    private MdmRecord(Long systemId, Long objectTypeId, Department department, String recordCode, Long actorId) {
        this.systemId = systemId; this.objectTypeId = objectTypeId; this.departmentId = department.getId();
        this.recordCode = recordCode; status = "active"; approvalStatus = "approved"; createdBy = actorId; updatedBy = actorId;
    }
    public static MdmRecord create(Long systemId, ObjectType ignored, Long objectTypeId, Department department,
                                   String recordCode, Long actorId) {
        return new MdmRecord(systemId, objectTypeId, department, recordCode, actorId);
    }
    public void touch(Long actorId) { updatedBy = actorId; updatedAt = LocalDateTime.now(); }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getObjectTypeId() { return objectTypeId; }
    public Long getDepartmentId() { return departmentId; }
    public String getRecordCode() { return recordCode; }
    public String getStatus() { return status; }
    public long getVersion() { return version == null ? 0L : version; }
}
