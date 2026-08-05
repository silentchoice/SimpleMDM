package com.example.mdm.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecordSnapshotCodec {
  private static final int SCHEMA_VERSION = 1;
  private final ObjectMapper json;

  public RecordSnapshotCodec(ObjectMapper json) {
    this.json = json;
  }

  public String encode(RecordDraft draft) {
    var children = draft.children().stream().map(group -> {
      var rows = group.rows().stream()
          .sorted(Comparator.comparingInt(RecordDraft.ChildRow::rowOrder))
          .map(row -> new SnapshotChildRow(row.recordId(), row.rowOrder(), row.values()))
          .toList();
      return new SnapshotChildRows(group.subTypeId(), rows);
    }).toList();
    return write(new Snapshot(SCHEMA_VERSION, draft.departmentId(), draft.masterTypeId(),
        draft.recordId(), draft.recordCode(), draft.action(), draft.baseVersion(),
        draft.masterValues(), children));
  }

  public Snapshot decode(String snapshot) {
    try {
      Snapshot decoded = json.readValue(snapshot, Snapshot.class);
      if (decoded.schemaVersion() != SCHEMA_VERSION) {
        throw new IllegalArgumentException("Unsupported record snapshot schema version: "
            + decoded.schemaVersion());
      }
      return decoded;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid record snapshot", exception);
    }
  }

  private String write(Snapshot snapshot) {
    try {
      return json.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize record snapshot", exception);
    }
  }

  public record Snapshot(int schemaVersion, long departmentId, long masterTypeId, Long recordId,
      String recordCode, RecordAction action, long baseVersion, Map<String, Object> masterValues,
      List<SnapshotChildRows> children) {
    public Snapshot {
      masterValues = immutableMap(masterValues);
      children = children == null ? List.of()
          : Collections.unmodifiableList(new ArrayList<>(children));
    }
  }

  public record SnapshotChildRows(long subTypeId, List<SnapshotChildRow> rows) {
    public SnapshotChildRows {
      rows = rows == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(rows));
    }
  }

  public record SnapshotChildRow(Long recordId, int rowOrder, Map<String, Object> values) {
    public SnapshotChildRow {
      values = immutableMap(values);
    }
  }

  private static Map<String, Object> immutableMap(Map<String, Object> values) {
    return values == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
