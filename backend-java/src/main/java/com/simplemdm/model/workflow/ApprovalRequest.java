package com.simplemdm.model.workflow;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wf_approval_request")
public class ApprovalRequest {
    public enum Operation { CREATE, UPDATE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="system_id", nullable=false) private Long systemId;
    @Column(name="object_type_id", nullable=false) private Long objectTypeId;
    @Column(name="record_id") private Long recordId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private Operation operation;
    @Column(name="record_code", length=128) private String recordCode;
    @Column(name="department_id", nullable=false) private Long departmentId;
    @Column(name="requested_by", nullable=false) private Long requestedBy;
    @Column(name="expected_version") private Long expectedVersion;
    @Column(nullable=false, length=32) private String status;
    @Column(name="submitted_at", nullable=false) private LocalDateTime submittedAt;
    @Column(name="decided_at") private LocalDateTime decidedAt;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Version @Column(nullable=false) private Long version;

    protected ApprovalRequest() { }
    public static ApprovalRequest pending(Long systemId, Long objectTypeId, Long recordId, Long departmentId,
                                          Long requestedBy, Long expectedVersion) {
        return pending(systemId, objectTypeId, Operation.UPDATE, recordId, null, departmentId, requestedBy, expectedVersion);
    }
    public static ApprovalRequest pending(Long systemId, Long objectTypeId, Operation operation, Long recordId,
                                          String recordCode, Long departmentId, Long requestedBy, Long expectedVersion) {
        ApprovalRequest value = new ApprovalRequest();
        value.systemId=systemId; value.objectTypeId=objectTypeId; value.operation=operation;
        value.recordId=recordId; value.recordCode=recordCode;
        value.departmentId=departmentId; value.requestedBy=requestedBy; value.expectedVersion=expectedVersion;
        value.status="PENDING";
        return value;
    }
    @PrePersist void create() { LocalDateTime now=LocalDateTime.now(); submittedAt=now; createdAt=now; updatedAt=now; }
    @PreUpdate void update() { updatedAt=LocalDateTime.now(); }
    public void approve() { status="APPROVED"; decidedAt=LocalDateTime.now(); }
    public void reject() { status="REJECTED"; decidedAt=LocalDateTime.now(); }
    public void attachCreatedRecord(Long createdRecordId) {
        if (operation != Operation.CREATE || recordId != null || createdRecordId == null) {
            throw new IllegalStateException("Only a create request can receive its effective record ID");
        }
        recordId = createdRecordId;
    }

    public Long getId(){return id;} public Long getSystemId(){return systemId;} public Long getObjectTypeId(){return objectTypeId;}
    public Long getRecordId(){return recordId;} public Long getDepartmentId(){return departmentId;}
    public Long getExpectedVersion(){return expectedVersion;} public Long getRequestedBy(){return requestedBy;} public String getStatus(){return status;}
    public Operation getOperation(){return operation;} public String getRecordCode(){return recordCode;}
    public LocalDateTime getDecidedAt(){return decidedAt;}
}
