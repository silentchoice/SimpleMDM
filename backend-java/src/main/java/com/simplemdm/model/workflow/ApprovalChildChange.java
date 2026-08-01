package com.simplemdm.model.workflow;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wf_approval_child_change", uniqueConstraints = {
    @UniqueConstraint(name = "uk_approval_child_change_key", columnNames = {"approval_request_id", "change_key"}),
    @UniqueConstraint(name = "uk_approval_child_system_id", columnNames = {"system_id", "id"})
})
public class ApprovalChildChange {
    public enum Operation { CREATE, UPDATE, DELETE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "approval_request_id", nullable = false) private Long approvalRequestId;
    @Column(name = "change_key", nullable = false, length = 128) private String changeKey;
    @Column(name = "child_type_id", nullable = false) private Long childTypeId;
    @Column(name = "child_record_id") private Long childRecordId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Operation operation;
    @Column(name = "expected_version") private Long expectedVersion;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected ApprovalChildChange() { }

    public static ApprovalChildChange create(Long systemId, Long approvalRequestId, String changeKey,
                                             Long childTypeId, Long childRecordId, Operation operation,
                                             Long expectedVersion, int sortOrder) {
        ApprovalChildChange change = new ApprovalChildChange();
        change.systemId = systemId;
        change.approvalRequestId = approvalRequestId;
        change.changeKey = changeKey;
        change.childTypeId = childTypeId;
        change.childRecordId = childRecordId;
        change.operation = operation;
        change.expectedVersion = expectedVersion;
        change.sortOrder = sortOrder;
        return change;
    }

    @PrePersist
    void create() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getApprovalRequestId() { return approvalRequestId; }
    public String getChangeKey() { return changeKey; }
    public Long getChildTypeId() { return childTypeId; }
    public Long getChildRecordId() { return childRecordId; }
    public Operation getOperation() { return operation; }
    public Long getExpectedVersion() { return expectedVersion; }
    public int getSortOrder() { return sortOrder; }
}
