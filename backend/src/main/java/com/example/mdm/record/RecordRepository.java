package com.example.mdm.record;

import java.util.List;

public interface RecordRepository {
  RecordDraft saveDraft(long departmentId, long actorId, RecordDraft draft);
  RecordDraft findDraft(long departmentId, long draftId);
  List<RecordDraft> findDrafts(long departmentId, long actorId);
  RecordView findRecord(long departmentId, long recordId);
  RecordView activate(long draftId, long actorId);
  void retainLatestHistory(long recordId, int keep);
}
