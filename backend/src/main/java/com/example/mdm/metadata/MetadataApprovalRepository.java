package com.example.mdm.metadata;

public interface MetadataApprovalRepository {
  record ApprovalTask(long id, long departmentId, String entityKind, long entityId,
      String beforeSnapshot, String afterSnapshot, String status) {}

  long submit(MetadataChangeRequest request);

  ApprovalTask lock(long taskId);

  void approve(long taskId, long reviewerId, String comment);

  void reject(long taskId, long reviewerId, String reason);

  long requireSubTypeTemplate(long departmentId, long subTypeId);
}
