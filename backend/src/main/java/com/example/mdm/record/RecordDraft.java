package com.example.mdm.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RecordDraft(long id, Long recordId, long masterTypeId, long departmentId,
    String recordCode, RecordAction action, long baseVersion, Map<String, Object> masterValues,
    List<ChildRows> children, RecordStatus status, long createdBy, String deleteReason) {
  public RecordDraft {
    masterValues = immutableMap(masterValues);
    children = children == null ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(children));
  }

  public record ChildRows(long subTypeId, List<ChildRow> rows) {
    public ChildRows {
      rows = rows == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(rows));
    }
  }

  public record ChildRow(Long recordId, int rowOrder, Map<String, Object> values) {
    public ChildRow {
      values = immutableMap(values);
    }
  }

  private static Map<String, Object> immutableMap(Map<String, Object> values) {
    return values == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
