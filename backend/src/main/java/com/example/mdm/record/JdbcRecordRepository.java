package com.example.mdm.record;

import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRecordRepository implements RecordRepository {
  private static final TypeReference<LinkedHashMap<String, Object>> VALUE_MAP =
      new TypeReference<>() {};
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;
  private final RecordSnapshotCodec snapshots;

  public JdbcRecordRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json,
      RecordSnapshotCodec snapshots) {
    this.jdbc = jdbc;
    this.json = json;
    this.snapshots = snapshots;
  }

  @Override
  @Transactional
  public RecordDraft saveDraft(long departmentId, long actorId, RecordDraft draft) {
    if (draft.departmentId() != departmentId) throw BusinessException.forbidden();
    long id = draft.id() == 0 ? insertDraft(departmentId, actorId, draft)
        : updateDraft(departmentId, draft);
    replaceDraftChildren(id, actorId, draft.children());
    return new RecordDraft(id, draft.recordId(), draft.masterTypeId(), departmentId,
        draft.recordCode(), draft.action(), draft.baseVersion(), draft.masterValues(),
        draft.children(), draft.status(), actorId, draft.deleteReason());
  }

  private long insertDraft(long departmentId, long actorId, RecordDraft draft) {
    var key = new GeneratedKeyHolder();
    var parameters = draftParameters(departmentId, actorId, draft)
        .addValue("status", RecordStatus.DRAFT.name());
    try {
      jdbc.update("INSERT INTO master_record_drafts(master_record_id,master_type_id,department_id,"
              + "record_code,record_action,field_values,version,base_version,delete_reason,status,"
              + "created_by) VALUES(:record,:type,:department,:code,:action,:values,0,:base,"
              + ":reason,:status,:actor)", parameters, key);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "An active draft already exists");
    }
    if (key.getKey() == null) throw new IllegalStateException("Draft id was not generated");
    return key.getKey().longValue();
  }

  private long updateDraft(long departmentId, RecordDraft draft) {
    var parameters = draftParameters(departmentId, draft.createdBy(), draft)
        .addValue("id", draft.id());
    int updated = jdbc.update("UPDATE master_record_drafts SET field_values=:values,"
            + "delete_reason=:reason,updated_at=CURRENT_TIMESTAMP WHERE id=:id "
            + "AND department_id=:department AND master_record_id<=>:record "
            + "AND master_type_id=:type AND record_code=:code AND record_action=:action "
            + "AND base_version<=>:base AND status='DRAFT'", parameters);
    if (updated != 1) throw notEditable();
    return draft.id();
  }

  private MapSqlParameterSource draftParameters(long departmentId, long actorId,
      RecordDraft draft) {
    return new MapSqlParameterSource()
        .addValue("record", draft.recordId(), Types.BIGINT)
        .addValue("type", draft.masterTypeId())
        .addValue("department", departmentId)
        .addValue("code", draft.recordCode())
        .addValue("action", draft.action().name())
        .addValue("values", writeValues(draft.masterValues()))
        .addValue("base", draft.baseVersion())
        .addValue("reason", draft.deleteReason(), Types.VARCHAR)
        .addValue("actor", actorId);
  }

  private void replaceDraftChildren(long draftId, long actorId,
      List<RecordDraft.ChildRows> children) {
    jdbc.update("DELETE FROM sub_record_drafts WHERE master_draft_id=:draft",
        new MapSqlParameterSource("draft", draftId));
    var rows = children.stream().flatMap(group -> group.rows().stream().map(row ->
        new DraftChild(group.subTypeId(), row))).toList();
    if (rows.isEmpty()) return;
    jdbc.getJdbcTemplate().batchUpdate("INSERT INTO sub_record_drafts(master_draft_id,"
        + "sub_record_id,sub_type_id,row_order,field_values,version,status,created_by) "
        + "VALUES(?,?,?,?,?,0,'DRAFT',?)", new BatchPreparedStatementSetter() {
          @Override public void setValues(PreparedStatement statement, int index)
              throws SQLException {
            var child = rows.get(index);
            statement.setLong(1, draftId);
            if (child.row().recordId() == null) statement.setNull(2, Types.BIGINT);
            else statement.setLong(2, child.row().recordId());
            statement.setLong(3, child.subTypeId());
            statement.setInt(4, child.row().rowOrder());
            statement.setString(5, writeValues(child.row().values()));
            statement.setLong(6, actorId);
          }
          @Override public int getBatchSize() { return rows.size(); }
        });
  }

  @Override
  public RecordDraft findDraft(long departmentId, long draftId) {
    var found = jdbc.query("SELECT id,master_record_id,master_type_id,department_id,record_code,"
            + "record_action,base_version,field_values,status,created_by,delete_reason "
            + "FROM master_record_drafts WHERE department_id=:department AND id=:id",
        Map.of("department", departmentId, "id", draftId), draftMapper());
    if (found.isEmpty()) {
      rejectForeignOrMissing("master_record_drafts", draftId, "Draft");
    }
    DraftHeader header = found.get(0);
    return toDraft(header, findDraftChildren(header.id()));
  }

  @Override
  public List<RecordDraft> findDrafts(long departmentId, long actorId) {
    var headers = jdbc.query("SELECT id,master_record_id,master_type_id,department_id,record_code,"
            + "record_action,base_version,field_values,status,created_by,delete_reason "
            + "FROM master_record_drafts WHERE department_id=:department AND created_by=:actor "
            + "AND status IN ('DRAFT','PENDING','REJECTED') ORDER BY updated_at DESC,id DESC",
        Map.of("department", departmentId, "actor", actorId), draftMapper());
    return headers.stream().map(header -> toDraft(header, findDraftChildren(header.id()))).toList();
  }

  @Override
  public RecordView findRecord(long departmentId, long recordId) {
    var found = jdbc.query("SELECT id,master_type_id,department_id,record_code,field_values,version,"
            + "status FROM master_records WHERE department_id=:department AND id=:id",
        Map.of("department", departmentId, "id", recordId), recordMapper());
    if (found.isEmpty()) {
      rejectForeignOrMissing("master_records", recordId, "Record");
    }
    RecordHeader header = found.get(0);
    return toView(header, findRecordChildren(header.id()));
  }

  private void rejectForeignOrMissing(String table, long id, String resource) {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=:id",
        Map.of("id", id), Integer.class);
    if (count != null && count > 0) throw BusinessException.forbidden();
    throw BusinessException.notFound(resource);
  }

  @Override
  @Transactional
  public RecordView activate(long draftId, long actorId) {
    var found = jdbc.query("SELECT id,master_record_id,master_type_id,department_id,record_code,"
            + "record_action,base_version,field_values,status,created_by,delete_reason "
            + "FROM master_record_drafts WHERE id=:id FOR UPDATE", Map.of("id", draftId),
        draftMapper());
    if (found.isEmpty()) {
      Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM master_record_drafts WHERE id=:id",
          Map.of("id", draftId), Integer.class);
      if (count == null || count == 0) throw BusinessException.notFound("Draft");
      throw notEditable();
    }
    DraftHeader header = found.get(0);
    RecordDraft draft = toDraft(header, findDraftChildren(header.id()));
    if (draft.status() != RecordStatus.PENDING) throw notEditable();

    RecordView previous = null;
    RecordView activated;
    if (draft.action() == RecordAction.CREATE) {
      activated = activateCreate(draft, actorId);
    } else {
      RecordHeader current = lockRecord(draft);
      previous = toView(current, findRecordChildren(current.id()));
      activated = draft.action() == RecordAction.UPDATE
          ? activateUpdate(draft, current, actorId) : activateDelete(draft, current, actorId);
    }
    int transitioned = jdbc.update("UPDATE master_record_drafts SET status='APPROVED',"
            + "updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status='PENDING'",
        new MapSqlParameterSource("id", draftId));
    if (transitioned != 1) throw notEditable();
    if (previous != null) {
      insertHistory(draft, previous, actorId);
      retainLatestHistory(previous.id(), 3);
    }
    return activated;
  }

  private RecordView activateCreate(RecordDraft draft, long actorId) {
    var key = new GeneratedKeyHolder();
    var parameters = new MapSqlParameterSource()
        .addValue("type", draft.masterTypeId()).addValue("department", draft.departmentId())
        .addValue("code", draft.recordCode()).addValue("values", writeValues(draft.masterValues()))
        .addValue("actor", actorId);
    try {
      jdbc.update("INSERT INTO master_records(master_type_id,department_id,record_code,field_values,"
              + "version,status,created_by) VALUES(:type,:department,:code,:values,1,'ACTIVE',:actor)",
          parameters, key);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "Record code already exists");
    }
    if (key.getKey() == null) throw new IllegalStateException("Record id was not generated");
    long recordId = key.getKey().longValue();
    var children = applyFormalChildren(recordId, draft.children(), 1L, actorId, false);
    return new RecordView(recordId, draft.masterTypeId(), draft.departmentId(), draft.recordCode(),
        draft.masterValues(), children, 1L, "ACTIVE");
  }

  private RecordView activateUpdate(RecordDraft draft, RecordHeader current, long actorId) {
    long version = current.version() + 1;
    int updated = jdbc.update("UPDATE master_records SET field_values=:values,version=:next,"
            + "status='ACTIVE',deleted_at=NULL WHERE id=:id AND department_id=:department "
            + "AND version=:base AND status='ACTIVE'",
        new MapSqlParameterSource().addValue("values", writeValues(draft.masterValues()))
            .addValue("next", version).addValue("id", current.id())
            .addValue("department", draft.departmentId()).addValue("base", draft.baseVersion()));
    if (updated != 1) throw versionConflict();
    var children = applyFormalChildren(current.id(), draft.children(), version, actorId, true);
    return new RecordView(current.id(), draft.masterTypeId(), draft.departmentId(),
        draft.recordCode(), draft.masterValues(), children, version, "ACTIVE");
  }

  private RecordView activateDelete(RecordDraft draft, RecordHeader current, long actorId) {
    long version = current.version() + 1;
    int updated = jdbc.update("UPDATE master_records SET version=:next,status='DELETED',"
            + "deleted_at=CURRENT_TIMESTAMP WHERE id=:id AND department_id=:department "
            + "AND version=:base AND status='ACTIVE'",
        new MapSqlParameterSource().addValue("next", version).addValue("id", current.id())
            .addValue("department", draft.departmentId()).addValue("base", draft.baseVersion()));
    if (updated != 1) throw versionConflict();
    jdbc.update("UPDATE sub_records SET status='DELETED',deleted_at=CURRENT_TIMESTAMP "
            + "WHERE master_record_id=:record AND status='ACTIVE'",
        new MapSqlParameterSource("record", current.id()));
    var children = draft.children().stream().map(group -> new RecordView.ChildRows(
        group.subTypeId(), group.rows().stream().filter(row -> row.recordId() != null)
            .map(row -> new RecordView.ChildRow(row.recordId(), row.rowOrder(), row.values()))
            .toList())).toList();
    return new RecordView(current.id(), draft.masterTypeId(), draft.departmentId(),
        draft.recordCode(), draft.masterValues(), children, version, "DELETED");
  }

  private RecordHeader lockRecord(RecordDraft draft) {
    var found = jdbc.query("SELECT id,master_type_id,department_id,record_code,field_values,version,"
            + "status FROM master_records WHERE id=:id AND department_id=:department FOR UPDATE",
        Map.of("id", draft.recordId(), "department", draft.departmentId()), recordMapper());
    if (found.isEmpty()) throw BusinessException.notFound("Record");
    RecordHeader current = found.get(0);
    if (current.masterTypeId() != draft.masterTypeId()
        || current.version() != draft.baseVersion()) throw versionConflict();
    if (!"ACTIVE".equals(current.status())) throw inactiveRecord();
    return current;
  }

  private List<RecordView.ChildRows> applyFormalChildren(long recordId,
      List<RecordDraft.ChildRows> groups, long version, long actorId, boolean replace) {
    if (replace) {
      jdbc.update("UPDATE sub_records SET status='DELETED',deleted_at=CURRENT_TIMESTAMP "
              + "WHERE master_record_id=:record AND status='ACTIVE'",
          new MapSqlParameterSource("record", recordId));
    }
    var result = new ArrayList<RecordView.ChildRows>();
    for (var group : groups) {
      var rows = new ArrayList<RecordView.ChildRow>();
      for (var row : group.rows()) {
        long id;
        if (row.recordId() == null) {
          var key = new GeneratedKeyHolder();
          jdbc.update("INSERT INTO sub_records(master_record_id,sub_type_id,row_order,field_values,"
                  + "version,status,created_by) VALUES(:record,:type,:position,:values,:version,"
                  + "'ACTIVE',:actor)",
              new MapSqlParameterSource().addValue("record", recordId)
                  .addValue("type", group.subTypeId()).addValue("position", row.rowOrder())
                  .addValue("values", writeValues(row.values())).addValue("version", version)
                  .addValue("actor", actorId), key);
          if (key.getKey() == null) throw new IllegalStateException("Child record id was not generated");
          id = key.getKey().longValue();
        } else {
          id = row.recordId();
          int updated = jdbc.update("UPDATE sub_records SET sub_type_id=:type,row_order=:position,"
                  + "field_values=:values,version=:version,status='ACTIVE',deleted_at=NULL "
                  + "WHERE id=:id AND master_record_id=:record",
              new MapSqlParameterSource().addValue("type", group.subTypeId())
                  .addValue("position", row.rowOrder()).addValue("values", writeValues(row.values()))
                  .addValue("version", version).addValue("id", id).addValue("record", recordId));
          if (updated != 1) throw new BusinessException(HttpStatus.CONFLICT,
              "Child record changed");
        }
        rows.add(new RecordView.ChildRow(id, row.rowOrder(), row.values()));
      }
      result.add(new RecordView.ChildRows(group.subTypeId(), rows));
    }
    return List.copyOf(result);
  }

  private void insertHistory(RecordDraft draft, RecordView previous, long actorId) {
    var historyChildren = previous.children().stream().map(group -> new RecordDraft.ChildRows(
        group.subTypeId(), group.rows().stream().map(row ->
            new RecordDraft.ChildRow(row.id(), row.rowOrder(), row.values())).toList())).toList();
    var history = new RecordDraft(draft.id(), previous.id(), previous.masterTypeId(),
        previous.departmentId(), previous.recordCode(), draft.action(), previous.version(),
        previous.masterValues(), historyChildren, RecordStatus.APPROVED, draft.createdBy(),
        draft.deleteReason());
    jdbc.update("INSERT INTO master_record_history(master_record_id,version,snapshot,status,changed_by) "
            + "VALUES(:record,:version,:snapshot,:status,:actor)",
        new MapSqlParameterSource().addValue("record", previous.id())
            .addValue("version", previous.version()).addValue("snapshot", snapshots.encode(history))
            .addValue("status", previous.status()).addValue("actor", actorId));
  }

  @Override
  public void retainLatestHistory(long recordId, int keep) {
    if (keep < 1) throw new IllegalArgumentException("History retention must be positive");
    jdbc.update("DELETE FROM master_record_history WHERE id IN (SELECT id FROM (SELECT id,"
            + "ROW_NUMBER() OVER (PARTITION BY master_record_id ORDER BY version DESC, id DESC) "
            + "AS ranked_position FROM master_record_history WHERE master_record_id=:record) "
            + "ranked WHERE ranked_position > :keep)",
        new MapSqlParameterSource().addValue("record", recordId).addValue("keep", keep));
  }

  private List<RecordDraft.ChildRows> findDraftChildren(long draftId) {
    var rows = jdbc.query("SELECT draft.sub_record_id,draft.sub_type_id,draft.row_order,"
            + "draft.field_values FROM sub_record_drafts draft JOIN sub_types type "
            + "ON type.id=draft.sub_type_id WHERE draft.master_draft_id=:draft "
            + "ORDER BY type.sort_order,type.id,draft.row_order,draft.id",
        Map.of("draft", draftId), (rs, rowNumber) -> new DraftChild(rs.getLong("sub_type_id"),
            new RecordDraft.ChildRow(rs.getObject("sub_record_id", Long.class),
                rs.getInt("row_order"), readValues(rs.getString("field_values")))));
    var grouped = new LinkedHashMap<Long, List<RecordDraft.ChildRow>>();
    rows.forEach(child -> grouped.computeIfAbsent(child.subTypeId(), ignored -> new ArrayList<>())
        .add(child.row()));
    return grouped.entrySet().stream()
        .map(entry -> new RecordDraft.ChildRows(entry.getKey(), entry.getValue())).toList();
  }

  private List<RecordView.ChildRows> findRecordChildren(long recordId) {
    record ViewChild(long subTypeId, RecordView.ChildRow row) {}
    var rows = jdbc.query("SELECT record.id,record.sub_type_id,record.row_order,record.field_values "
            + "FROM sub_records record JOIN sub_types type ON type.id=record.sub_type_id "
            + "WHERE record.master_record_id=:record AND record.status='ACTIVE' "
            + "ORDER BY type.sort_order,type.id,record.row_order,record.id",
        Map.of("record", recordId), (rs, rowNumber) -> new ViewChild(rs.getLong("sub_type_id"),
            new RecordView.ChildRow(rs.getLong("id"), rs.getInt("row_order"),
                readValues(rs.getString("field_values")))));
    var grouped = new LinkedHashMap<Long, List<RecordView.ChildRow>>();
    rows.forEach(child -> grouped.computeIfAbsent(child.subTypeId(), ignored -> new ArrayList<>())
        .add(child.row()));
    return grouped.entrySet().stream()
        .map(entry -> new RecordView.ChildRows(entry.getKey(), entry.getValue())).toList();
  }

  private RowMapper<DraftHeader> draftMapper() {
    return (rs, rowNumber) -> new DraftHeader(rs.getLong("id"),
        rs.getObject("master_record_id", Long.class), rs.getLong("master_type_id"),
        rs.getLong("department_id"), rs.getString("record_code"),
        RecordAction.valueOf(rs.getString("record_action")), rs.getLong("base_version"),
        readValues(rs.getString("field_values")), RecordStatus.valueOf(rs.getString("status")),
        rs.getLong("created_by"), rs.getString("delete_reason"));
  }

  private RowMapper<RecordHeader> recordMapper() {
    return (rs, rowNumber) -> new RecordHeader(rs.getLong("id"), rs.getLong("master_type_id"),
        rs.getLong("department_id"), rs.getString("record_code"),
        readValues(rs.getString("field_values")), rs.getLong("version"),
        rs.getString("status"));
  }

  private RecordDraft toDraft(DraftHeader header, List<RecordDraft.ChildRows> children) {
    return new RecordDraft(header.id(), header.recordId(), header.masterTypeId(),
        header.departmentId(), header.recordCode(), header.action(), header.baseVersion(),
        header.values(), children, header.status(), header.createdBy(), header.deleteReason());
  }

  private RecordView toView(RecordHeader header, List<RecordView.ChildRows> children) {
    return new RecordView(header.id(), header.masterTypeId(), header.departmentId(),
        header.recordCode(), header.values(), children, header.version(), header.status());
  }

  private Map<String, Object> readValues(String values) {
    try {
      return json.readValue(values, VALUE_MAP);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid record field values", exception);
    }
  }

  private String writeValues(Map<String, Object> values) {
    try {
      return json.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize record field values", exception);
    }
  }

  private BusinessException versionConflict() {
    return new BusinessException(HttpStatus.CONFLICT, "Record version changed");
  }

  private BusinessException inactiveRecord() {
    return new BusinessException(HttpStatus.CONFLICT, "Record is no longer active");
  }

  private BusinessException notEditable() {
    return new BusinessException(HttpStatus.CONFLICT, "Draft is no longer editable");
  }

  private record DraftChild(long subTypeId, RecordDraft.ChildRow row) {}
  private record DraftHeader(long id, Long recordId, long masterTypeId, long departmentId,
      String recordCode, RecordAction action, long baseVersion, Map<String, Object> values,
      RecordStatus status, long createdBy, String deleteReason) {}
  private record RecordHeader(long id, long masterTypeId, long departmentId, String recordCode,
      Map<String, Object> values, long version, String status) {}
}
