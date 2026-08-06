package com.example.mdm.record;

import com.example.mdm.common.error.BusinessException;
import com.example.mdm.metadata.MetadataApprovalRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRecordApprovalRepository implements RecordApprovalRepository {
  private static final String VIEW_COLUMNS = "task.id,task.entity_type,task.entity_id,task.status,"
      + "task.before_snapshot,task.after_snapshot,task.submitted_by,task.reviewed_by,"
      + "task.review_comment,task.submitted_at,task.reviewed_at";
  private static final RowMapper<MetadataApprovalRepository.ApprovalTaskView> VIEW_MAPPER =
      (result, row) -> new MetadataApprovalRepository.ApprovalTaskView(
          result.getLong("id"), "RECORD", result.getString("entity_type"),
          result.getLong("entity_id"), result.getString("status"),
          result.getString("before_snapshot"), result.getString("after_snapshot"),
          result.getLong("submitted_by"), result.getObject("reviewed_by", Long.class),
          result.getString("review_comment"),
          result.getObject("submitted_at", LocalDateTime.class),
          result.getObject("reviewed_at", LocalDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;
  private final RecordRepository records;

  public JdbcRecordApprovalRepository(NamedParameterJdbcTemplate jdbc, RecordRepository records) {
    this.jdbc = jdbc;
    this.records = records;
  }

  @Override
  public BoundDraft lockDraft(long departmentId, long draftId) {
    var rows = jdbc.query("SELECT id,master_record_id FROM master_record_drafts "
            + "WHERE department_id=:department AND id=:id FOR UPDATE",
        Map.of("department", departmentId, "id", draftId),
        (result, row) -> result.getObject("master_record_id", Long.class));
    if (rows.isEmpty()) {
      rejectForeignOrMissing("master_record_drafts", draftId, "Draft");
    }
    RecordDraft draft = records.findDraft(departmentId, draftId);
    RecordView formal = null;
    if (draft.recordId() != null) {
      var formalRows = jdbc.query("SELECT id FROM master_records WHERE department_id=:department "
              + "AND id=:id FOR UPDATE",
          Map.of("department", departmentId, "id", draft.recordId()),
          (result, row) -> result.getLong("id"));
      if (formalRows.isEmpty()) rejectForeignOrMissing("master_records", draft.recordId(), "Record");
      formal = records.findRecord(departmentId, draft.recordId());
    }
    return new BoundDraft(draft, formal);
  }

  @Override
  public long submit(long departmentId, long submitterId, long draftId, String beforeSnapshot,
      String afterSnapshot) {
    var key = new GeneratedKeyHolder();
    try {
      jdbc.update("INSERT INTO approval_tasks(department_id,entity_type,entity_id,before_snapshot,"
              + "after_snapshot,status,submitted_by) VALUES(:department,'RECORD',:draft,:before,"
              + ":after,'PENDING',:submitter)",
          new MapSqlParameterSource().addValue("department", departmentId)
              .addValue("draft", draftId).addValue("before", beforeSnapshot)
              .addValue("after", afterSnapshot).addValue("submitter", submitterId), key);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "Record approval conflict");
    }
    if (key.getKey() == null) throw new IllegalStateException("Approval task id was not generated");
    return key.getKey().longValue();
  }

  @Override
  public void markPending(long departmentId, long draftId, long taskId) {
    int updated = jdbc.update("UPDATE master_record_drafts SET status='PENDING',"
            + "approval_task_id=:task,updated_at=CURRENT_TIMESTAMP WHERE department_id=:department "
            + "AND id=:draft AND status='DRAFT' AND approval_task_id IS NULL",
        Map.of("task", taskId, "department", departmentId, "draft", draftId));
    if (updated != 1) throw draftNotEditable();
  }

  @Override
  public ApprovalTask lockTask(long departmentId, long taskId) {
    var tasks = jdbc.query("SELECT task.id,task.department_id,task.entity_id,task.before_snapshot,"
            + "task.after_snapshot,task.status,task.submitted_by FROM approval_tasks task "
            + "JOIN master_record_drafts draft ON draft.approval_task_id=task.id "
            + "AND draft.id=task.entity_id WHERE task.department_id=:department AND task.id=:id "
            + "AND task.entity_type='RECORD' FOR UPDATE",
        Map.of("department", departmentId, "id", taskId), (result, row) -> new ApprovalTask(
            result.getLong("id"), result.getLong("department_id"), result.getLong("entity_id"),
            result.getString("before_snapshot"), result.getString("after_snapshot"),
            result.getString("status"), result.getLong("submitted_by")));
    if (!tasks.isEmpty()) return tasks.get(0);
    Integer matches = jdbc.queryForObject("SELECT COUNT(*) FROM approval_tasks WHERE id=:id "
        + "AND entity_type='RECORD'", Map.of("id", taskId), Integer.class);
    if (matches != null && matches > 0) throw BusinessException.forbidden();
    throw BusinessException.notFound("Approval task");
  }

  @Override
  public void approve(long departmentId, long taskId, long reviewerId, String comment) {
    transitionTask(departmentId, taskId, reviewerId, "APPROVED", comment);
  }

  @Override
  public void reject(long departmentId, long taskId, long draftId, long reviewerId, String reason) {
    transitionTask(departmentId, taskId, reviewerId, "REJECTED", reason);
    int updated = jdbc.update("UPDATE master_record_drafts SET status='REJECTED',"
            + "updated_at=CURRENT_TIMESTAMP WHERE department_id=:department AND id=:draft "
            + "AND approval_task_id=:task AND status='PENDING'",
        Map.of("department", departmentId, "draft", draftId, "task", taskId));
    if (updated != 1) throw new BusinessException(HttpStatus.CONFLICT, "Draft is no longer editable");
  }

  private void transitionTask(long departmentId, long taskId, long reviewerId, String status,
      String comment) {
    int updated = jdbc.update("UPDATE approval_tasks SET status=:status,reviewed_by=:reviewer,"
            + "review_comment=:comment,reviewed_at=CURRENT_TIMESTAMP WHERE department_id=:department "
            + "AND id=:id AND entity_type='RECORD' AND status='PENDING'",
        new MapSqlParameterSource().addValue("status", status).addValue("reviewer", reviewerId)
            .addValue("comment", comment).addValue("department", departmentId)
            .addValue("id", taskId));
    if (updated != 1) {
      throw new BusinessException(HttpStatus.CONFLICT, "Approval task is not pending");
    }
  }

  @Override
  public List<MetadataApprovalRepository.ApprovalTaskView> list(long departmentId, String status) {
    return jdbc.query("SELECT " + VIEW_COLUMNS + " FROM approval_tasks task "
            + "WHERE task.department_id=:department AND task.status=:status "
            + "AND task.entity_type='RECORD' ORDER BY task.submitted_at DESC,task.id DESC",
        Map.of("department", departmentId, "status", status), VIEW_MAPPER);
  }

  @Override
  public MetadataApprovalRepository.ApprovalTaskView detail(long departmentId, long taskId) {
    var tasks = jdbc.query("SELECT " + VIEW_COLUMNS + " FROM approval_tasks task "
            + "WHERE task.department_id=:department AND task.id=:id "
            + "AND task.entity_type='RECORD'",
        Map.of("department", departmentId, "id", taskId), VIEW_MAPPER);
    if (!tasks.isEmpty()) return tasks.get(0);
    Integer matches = jdbc.queryForObject("SELECT COUNT(*) FROM approval_tasks WHERE id=:id "
        + "AND entity_type='RECORD'", Map.of("id", taskId), Integer.class);
    if (matches != null && matches > 0) throw BusinessException.forbidden();
    throw BusinessException.notFound("Approval task");
  }

  private void rejectForeignOrMissing(String table, long id, String resource) {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=:id",
        Map.of("id", id), Integer.class);
    if (count != null && count > 0) throw BusinessException.forbidden();
    throw BusinessException.notFound(resource);
  }

  private BusinessException draftNotEditable() {
    return new BusinessException(HttpStatus.CONFLICT, "Draft is no longer editable");
  }
}
