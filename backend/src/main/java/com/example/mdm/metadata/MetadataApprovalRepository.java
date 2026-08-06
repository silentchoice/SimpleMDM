package com.example.mdm.metadata;

import java.time.LocalDateTime;
import java.util.List;

public interface MetadataApprovalRepository {
  record ApprovalTask(long id, long departmentId, String entityKind, long entityId,
      String beforeSnapshot, String afterSnapshot, String status) {}

  record ApprovalTaskView(long id, String taskType, String entityKind, long entityId, String status,
      String beforeSnapshot, String afterSnapshot, long submittedBy, Long reviewedBy,
      String reviewComment, LocalDateTime submittedAt, LocalDateTime reviewedAt) {
    public ApprovalTaskView(long id, String entityKind, long entityId, String status,
        String beforeSnapshot, String afterSnapshot, long submittedBy, Long reviewedBy,
        String reviewComment, LocalDateTime submittedAt, LocalDateTime reviewedAt) {
      this(id, "METADATA", entityKind, entityId, status, beforeSnapshot, afterSnapshot,
          submittedBy, reviewedBy, reviewComment, submittedAt, reviewedAt);
    }
  }

  long submit(MetadataChangeRequest request);

  ApprovalTask lock(long departmentId, long taskId);

  List<ApprovalTaskView> list(long departmentId, String status);

  ApprovalTaskView detail(long departmentId, long taskId);

  void approve(long departmentId, long taskId, long reviewerId, String comment);

  void reject(long departmentId, long taskId, long reviewerId, String reason);

  long requireSubTypeTemplate(long departmentId, long subTypeId);
}
