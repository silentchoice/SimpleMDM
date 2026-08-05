package com.example.mdm.record;

public interface RecordRepository {
  RecordDraft saveDraft(long departmentId, long actorId, RecordDraft draft);
  RecordDraft findDraft(long departmentId, long draftId);
  RecordView findRecord(long departmentId, long recordId);
  RecordView activate(long draftId, long actorId);
  void retainLatestHistory(long recordId, int keep);
}
