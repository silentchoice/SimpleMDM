package com.example.mdm.record;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecordQueryService {
  private static final int MAX_PAGE_SIZE = 100;
  private static final int HISTORY_LIMIT = 3;
  private static final TypeReference<LinkedHashMap<String, Object>> VALUE_MAP =
      new TypeReference<>() {};
  private static final Map<String, Comparator<StoredRecord>> SORTS = Map.of(
      "id", Comparator.comparingLong(item -> item.view().id()),
      "recordCode", Comparator.comparing(item -> item.view().recordCode(),
          String.CASE_INSENSITIVE_ORDER),
      "masterTypeId", Comparator.comparingLong(item -> item.view().masterTypeId()),
      "version", Comparator.comparingLong(item -> item.view().version()),
      "status", Comparator.comparing(item -> item.view().status()),
      "updatedAt", Comparator.comparing(StoredRecord::updatedAt));

  private final RecordSource source;
  private final RecordVisibilityService visibility;
  private final AuthorizationService authorization;

  public RecordQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper json,
      RecordSnapshotCodec snapshots, RecordVisibilityService visibility,
      AuthorizationService authorization) {
    this(new JdbcRecordSource(jdbc, json, snapshots), visibility, authorization);
  }

  RecordQueryService(RecordSource source, RecordVisibilityService visibility,
      AuthorizationService authorization) {
    this.source = source;
    this.visibility = visibility;
    this.authorization = authorization;
  }

  public Paged<RecordView> list(RecordQuery query) {
    UserPrincipal actor = reader();
    RecordQuery normalized = normalize(query);
    Long departmentId = actor.department() == null ? null : actor.department().id();
    Comparator<StoredRecord> comparator = SORTS.get(normalized.sortBy());
    if ("desc".equals(normalized.sortDirection())) comparator = comparator.reversed();

    List<StoredRecord> visible = source.records().stream()
        .filter(item -> normalized.includeDeleted() || "ACTIVE".equals(item.view().status()))
        .filter(item -> normalized.masterTypeId() == null
            || item.view().masterTypeId() == normalized.masterTypeId())
        .filter(item -> normalized.status() == null
            || item.view().status().equals(normalized.status()))
        .filter(item -> normalized.updatedFrom() == null
            || !item.updatedAt().isBefore(normalized.updatedFrom()))
        .filter(item -> normalized.updatedTo() == null
            || !item.updatedAt().isAfter(normalized.updatedTo()))
        .map(item -> new StoredRecord(visibility.filter(item.view(), departmentId), item.updatedAt()))
        .filter(item -> contains(item.view().recordCode(), normalized.recordCode()))
        .filter(item -> keywordMatches(item.view(), normalized.keyword()))
        .sorted(comparator).toList();

    long start = (long) normalized.page() * normalized.size();
    int from = start >= visible.size() ? visible.size() : (int) start;
    int to = Math.min(visible.size(), from + normalized.size());
    List<RecordView> content = visible.subList(from, to).stream().map(StoredRecord::view).toList();
    long total = visible.size();
    int totalPages = total == 0 ? 0 : (int) ((total + normalized.size() - 1) / normalized.size());
    return new Paged<>(content, normalized.page(), normalized.size(), total, totalPages);
  }

  public RecordView detail(long recordId) {
    UserPrincipal actor = reader();
    Long departmentId = actor.department() == null ? null : actor.department().id();
    return visibility.filter(source.record(recordId), departmentId);
  }

  public List<RecordView> history(long recordId) {
    UserPrincipal actor = reader();
    Long departmentId = actor.department() == null ? null : actor.department().id();
    source.record(recordId);
    return source.history(recordId, HISTORY_LIMIT).stream()
        .map(view -> visibility.filter(view, departmentId)).toList();
  }

  private RecordQuery normalize(RecordQuery query) {
    if (query == null) throw BusinessException.badRequest("Record query is required");
    if (query.page() < 0 || query.size() <= 0) {
      throw BusinessException.badRequest("Invalid pagination");
    }
    String sort = blankToDefault(query.sortBy(), "updatedAt");
    if (!SORTS.containsKey(sort)) throw BusinessException.badRequest("Invalid sort column");
    String direction = blankToDefault(query.sortDirection(), "desc").toLowerCase(Locale.ROOT);
    if (!direction.equals("asc") && !direction.equals("desc")) {
      throw BusinessException.badRequest("Invalid sort direction");
    }
    String status = trimToNull(query.status());
    if (status != null) {
      status = status.toUpperCase(Locale.ROOT);
      if (!status.equals("ACTIVE") && !status.equals("DELETED")) {
        throw BusinessException.badRequest("Invalid record status");
      }
    }
    if (query.updatedFrom() != null && query.updatedTo() != null
        && query.updatedFrom().isAfter(query.updatedTo())) {
      throw BusinessException.badRequest("Invalid updated time range");
    }
    return new RecordQuery(query.masterTypeId(), trimToNull(query.recordCode()),
        trimToNull(query.keyword()), status, query.includeDeleted(), query.page(),
        Math.min(query.size(), MAX_PAGE_SIZE), sort, direction, query.updatedFrom(),
        query.updatedTo());
  }

  private boolean keywordMatches(RecordView view, String keyword) {
    if (keyword == null) return true;
    if (contains(view.recordCode(), keyword)) return true;
    return containsValue(view.masterValues(), keyword) || view.children().stream()
        .flatMap(group -> group.rows().stream()).anyMatch(row -> containsValue(row.values(), keyword));
  }

  private boolean containsValue(Object value, String keyword) {
    if (value instanceof Map<?, ?> values) {
      return values.values().stream().anyMatch(item -> containsValue(item, keyword));
    }
    if (value instanceof Iterable<?> values) {
      for (Object item : values) if (containsValue(item, keyword)) return true;
      return false;
    }
    return value != null && contains(String.valueOf(value), keyword);
  }

  private boolean contains(String value, String expected) {
    return expected == null || (value != null
        && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String blankToDefault(String value, String fallback) {
    String normalized = trimToNull(value);
    return normalized == null ? fallback : normalized;
  }

  private UserPrincipal reader() {
    return authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
        Role.DEPT_VIEWER);
  }

  public record RecordQuery(Long masterTypeId, String recordCode, String keyword, String status,
      boolean includeDeleted, int page, int size, String sortBy, String sortDirection,
      LocalDateTime updatedFrom, LocalDateTime updatedTo) {
    public RecordQuery(Long masterTypeId, String recordCode, String keyword, String status,
        boolean includeDeleted, int page, int size, String sortBy, String sortDirection) {
      this(masterTypeId, recordCode, keyword, status, includeDeleted, page, size, sortBy,
          sortDirection, null, null);
    }
  }

  public record Paged<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public Paged { content = List.copyOf(content); }
  }

  record StoredRecord(RecordView view, LocalDateTime updatedAt) {}

  interface RecordSource {
    List<StoredRecord> records();
    RecordView record(long recordId);
    List<RecordView> history(long recordId, int limit);
  }

  private static final class JdbcRecordSource implements RecordSource {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RecordSnapshotCodec snapshots;

    private JdbcRecordSource(NamedParameterJdbcTemplate jdbc, ObjectMapper json,
        RecordSnapshotCodec snapshots) {
      this.jdbc = jdbc;
      this.json = json;
      this.snapshots = snapshots;
    }

    @Override public List<StoredRecord> records() {
      return jdbc.query("SELECT id,master_type_id,department_id,record_code,field_values,version,"
              + "status,updated_at FROM master_records WHERE status IN ('ACTIVE','DELETED')",
          Map.of(), (result, row) -> {
            RecordView header = header(result.getLong("id"), result.getLong("master_type_id"),
                result.getLong("department_id"), result.getString("record_code"),
                result.getString("field_values"), result.getLong("version"),
                result.getString("status"));
            return new StoredRecord(withChildren(header),
                result.getObject("updated_at", LocalDateTime.class));
          });
    }

    @Override public RecordView record(long recordId) {
      var records = jdbc.query("SELECT id,master_type_id,department_id,record_code,field_values,"
              + "version,status FROM master_records WHERE id=:id AND status IN ('ACTIVE','DELETED')",
          Map.of("id", recordId), (result, row) -> header(result.getLong("id"),
              result.getLong("master_type_id"), result.getLong("department_id"),
              result.getString("record_code"), result.getString("field_values"),
              result.getLong("version"), result.getString("status")));
      if (records.isEmpty()) throw BusinessException.notFound("Record");
      return withChildren(records.get(0));
    }

    @Override public List<RecordView> history(long recordId, int limit) {
      return jdbc.query("SELECT master_record_id,version,snapshot,status FROM master_record_history "
              + "WHERE master_record_id=:record ORDER BY version DESC,id DESC LIMIT :limit",
          new MapSqlParameterSource().addValue("record", recordId).addValue("limit", limit),
          (result, row) -> {
            RecordSnapshotCodec.Snapshot snapshot = snapshots.decode(result.getString("snapshot"));
            var children = snapshot.children().stream().map(group -> new RecordView.ChildRows(
                group.subTypeId(), group.rows().stream().map(child -> new RecordView.ChildRow(
                    Objects.requireNonNull(child.recordId()), child.rowOrder(), child.values()))
                    .toList())).toList();
            return new RecordView(result.getLong("master_record_id"), snapshot.masterTypeId(),
                snapshot.departmentId(), snapshot.recordCode(), snapshot.masterValues(), children,
                result.getLong("version"), result.getString("status"));
          });
    }

    private RecordView header(long id, long masterTypeId, long departmentId, String recordCode,
        String values, long version, String status) {
      return new RecordView(id, masterTypeId, departmentId, recordCode, readValues(values),
          List.of(), version, status);
    }

    private RecordView withChildren(RecordView header) {
      record Child(long subTypeId, RecordView.ChildRow row) {}
      List<Child> rows = jdbc.query("SELECT id,sub_type_id,row_order,field_values FROM sub_records "
              + "WHERE master_record_id=:record AND status='ACTIVE' "
              + "ORDER BY sub_type_id,row_order,id", Map.of("record", header.id()),
          (result, row) -> new Child(result.getLong("sub_type_id"), new RecordView.ChildRow(
              result.getLong("id"), result.getInt("row_order"),
              readValues(result.getString("field_values")))));
      var grouped = new LinkedHashMap<Long, List<RecordView.ChildRow>>();
      rows.forEach(child -> grouped.computeIfAbsent(child.subTypeId(), ignored -> new ArrayList<>())
          .add(child.row()));
      var children = grouped.entrySet().stream()
          .map(entry -> new RecordView.ChildRows(entry.getKey(), entry.getValue())).toList();
      return new RecordView(header.id(), header.masterTypeId(), header.departmentId(),
          header.recordCode(), header.masterValues(), children, header.version(), header.status());
    }

    private Map<String, Object> readValues(String values) {
      try {
        return json.readValue(values, VALUE_MAP);
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("Invalid record values", exception);
      }
    }
  }
}
