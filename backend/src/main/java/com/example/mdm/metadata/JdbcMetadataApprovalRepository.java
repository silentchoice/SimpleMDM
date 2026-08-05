package com.example.mdm.metadata;

import com.example.mdm.common.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMetadataApprovalRepository implements MetadataApprovalRepository {
  private static final String VIEW_COLUMNS = "id,entity_type,entity_id,status,before_snapshot,"
      + "after_snapshot,submitted_by,reviewed_by,review_comment,submitted_at,reviewed_at";
  private static final RowMapper<ApprovalTaskView> VIEW_MAPPER = (result, row) ->
      new ApprovalTaskView(result.getLong("id"), result.getString("entity_type"),
          result.getLong("entity_id"), result.getString("status"),
          result.getString("before_snapshot"), result.getString("after_snapshot"),
          result.getLong("submitted_by"), result.getObject("reviewed_by", Long.class),
          result.getString("review_comment"),
          result.getObject("submitted_at", LocalDateTime.class),
          result.getObject("reviewed_at", LocalDateTime.class));
  private final NamedParameterJdbcTemplate jdbc;

  JdbcMetadataApprovalRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long submit(MetadataChangeRequest request) {
    var key = new GeneratedKeyHolder();
    var parameters = new MapSqlParameterSource()
        .addValue("department", request.departmentId())
        .addValue("kind", request.entityKind())
        .addValue("entity", request.entityId())
        .addValue("before", request.beforeSnapshot())
        .addValue("after", request.afterSnapshot())
        .addValue("submitter", request.submittedBy());
    try {
      jdbc.update("INSERT INTO approval_tasks(department_id,entity_type,entity_id,"
          + "before_snapshot,after_snapshot,status,submitted_by) VALUES("
          + ":department,:kind,:entity,:before,:after,'PENDING',:submitter)", parameters, key);
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(HttpStatus.CONFLICT,
          "Pending metadata approval already exists");
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "Metadata approval conflict");
    }
    return key.getKey().longValue();
  }

  @Override
  public ApprovalTask lock(long departmentId, long taskId) {
    var tasks = jdbc.query("SELECT id,department_id,entity_type,entity_id,before_snapshot,"
            + "after_snapshot,status FROM approval_tasks WHERE department_id=:department "
            + "AND id=:id FOR UPDATE", Map.of("department", departmentId, "id", taskId),
        (result, row) -> new ApprovalTask(result.getLong("id"), result.getLong("department_id"),
            result.getString("entity_type"), result.getLong("entity_id"),
            result.getString("before_snapshot"), result.getString("after_snapshot"),
            result.getString("status")));
    if (tasks.isEmpty()) {
      Integer matches = jdbc.queryForObject("SELECT COUNT(*) FROM approval_tasks WHERE id=:id",
          Map.of("id", taskId), Integer.class);
      if (matches != null && matches > 0) {
        throw BusinessException.forbidden();
      }
      throw BusinessException.notFound("Approval task");
    }
    return tasks.get(0);
  }

  @Override
  public List<ApprovalTaskView> list(long departmentId, String status) {
    return jdbc.query("SELECT " + VIEW_COLUMNS + " FROM approval_tasks "
            + "WHERE department_id=:department AND status=:status "
            + "ORDER BY submitted_at DESC,id DESC",
        Map.of("department", departmentId, "status", status), VIEW_MAPPER);
  }

  @Override
  public ApprovalTaskView detail(long departmentId, long taskId) {
    var tasks = jdbc.query("SELECT " + VIEW_COLUMNS + " FROM approval_tasks "
            + "WHERE department_id=:department AND id=:id",
        Map.of("department", departmentId, "id", taskId), VIEW_MAPPER);
    if (!tasks.isEmpty()) {
      return tasks.get(0);
    }
    Integer matches = jdbc.queryForObject("SELECT COUNT(*) FROM approval_tasks WHERE id=:id",
        Map.of("id", taskId), Integer.class);
    if (matches != null && matches > 0) {
      throw BusinessException.forbidden();
    }
    throw BusinessException.notFound("Approval task");
  }

  @Override
  public void approve(long departmentId, long taskId, long reviewerId, String comment) {
    transition(departmentId, taskId, reviewerId, "APPROVED", comment);
  }

  @Override
  public void reject(long departmentId, long taskId, long reviewerId, String reason) {
    transition(departmentId, taskId, reviewerId, "REJECTED", reason);
  }

  private void transition(long departmentId, long taskId, long reviewerId, String status,
      String comment) {
    int updated = jdbc.update("UPDATE approval_tasks SET status=:status,reviewed_by=:reviewer,"
            + "review_comment=:comment,reviewed_at=CURRENT_TIMESTAMP "
            + "WHERE department_id=:department AND id=:id AND status='PENDING'",
        new MapSqlParameterSource().addValue("status", status).addValue("reviewer", reviewerId)
            .addValue("comment", comment).addValue("department", departmentId)
            .addValue("id", taskId));
    if (updated != 1) {
      throw new BusinessException(HttpStatus.CONFLICT, "Approval task is not pending");
    }
  }

  @Override
  public long requireSubTypeTemplate(long departmentId, long subTypeId) {
    var templateIds = jdbc.query("SELECT master_type_id FROM sub_types WHERE department_id=:department "
            + "AND id=:id AND status='ACTIVE'", Map.of("department", departmentId, "id", subTypeId),
        (result, row) -> result.getLong("master_type_id"));
    if (templateIds.isEmpty()) {
      Integer foreignMatches = jdbc.queryForObject(
          "SELECT COUNT(*) FROM sub_types WHERE department_id<>:department AND id=:id "
              + "AND status='ACTIVE'", Map.of("department", departmentId, "id", subTypeId),
          Integer.class);
      if (foreignMatches != null && foreignMatches > 0) {
        throw BusinessException.forbidden();
      }
      throw BusinessException.notFound("Sub type");
    }
    return templateIds.get(0);
  }
}
