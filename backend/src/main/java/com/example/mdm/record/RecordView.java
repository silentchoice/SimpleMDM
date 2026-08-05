package com.example.mdm.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RecordView(long id, long masterTypeId, long departmentId, String recordCode,
    Map<String, Object> masterValues, List<ChildRows> children, long version, String status) {
  public RecordView {
    masterValues = immutableMap(masterValues);
    children = children == null ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(children));
  }

  public record ChildRows(long subTypeId, List<ChildRow> rows) {
    public ChildRows {
      rows = rows == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(rows));
    }
  }

  public record ChildRow(long id, int rowOrder, Map<String, Object> values) {
    public ChildRow {
      values = immutableMap(values);
    }
  }

  private static Map<String, Object> immutableMap(Map<String, Object> values) {
    return values == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
