package com.example.mdm.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RecordDraftCommand(Long recordId, long masterTypeId, long baseVersion,
    RecordAction action, Map<String, Object> masterValues, List<ChildRows> children,
    String deleteReason) {
  public RecordDraftCommand {
    masterValues = immutableMap(masterValues);
    children = children == null ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(children));
  }

  public record ChildRows(long subTypeId, List<ChildRowCommand> rows) {
    public ChildRows {
      rows = rows == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(rows));
    }
  }

  public record ChildRowCommand(Long recordId, int rowOrder, Map<String, Object> values) {
    public ChildRowCommand {
      values = immutableMap(values);
    }
  }

  private static Map<String, Object> immutableMap(Map<String, Object> values) {
    return values == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
