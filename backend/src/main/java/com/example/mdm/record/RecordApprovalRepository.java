package com.example.mdm.record;

import com.example.mdm.metadata.MetadataApprovalRepository;
import java.util.List;

public interface RecordApprovalRepository {
  record ApprovalTask(long id, long departmentId, long draftId, String beforeSnapshot,
      String afterSnapshot, String status, long submittedBy) {}

  record BoundDraft(RecordDraft draft, RecordView formal) {}

  BoundDraft lockDraft(long departmentId, long draftId);

  long submit(long departmentId, long submitterId, long draftId, String beforeSnapshot,
      String afterSnapshot);

  void markPending(long departmentId, long draftId, long taskId);

  ApprovalTask lockTask(long departmentId, long taskId);

  void approve(long departmentId, long taskId, long reviewerId, String comment);

  void reject(long departmentId, long taskId, long draftId, long reviewerId, String reason);

  List<MetadataApprovalRepository.ApprovalTaskView> list(long departmentId, String status);

  MetadataApprovalRepository.ApprovalTaskView detail(long departmentId, long taskId);
}
