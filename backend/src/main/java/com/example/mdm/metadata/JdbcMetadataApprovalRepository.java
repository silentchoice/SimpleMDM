package com.example.mdm.metadata;

import com.example.mdm.common.error.BusinessException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMetadataApprovalRepository implements MetadataApprovalRepository {
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
  public ApprovalTask lock(long taskId) {
    var tasks = jdbc.query("SELECT id,department_id,entity_type,entity_id,before_snapshot,"
            + "after_snapshot,status FROM approval_tasks WHERE id=:id FOR UPDATE", Map.of("id", taskId),
        (result, row) -> new ApprovalTask(result.getLong("id"), result.getLong("department_id"),
            result.getString("entity_type"), result.getLong("entity_id"),
            result.getString("before_snapshot"), result.getString("after_snapshot"),
            result.getString("status")));
    if (tasks.isEmpty()) {
      throw BusinessException.notFound("Approval task");
    }
    return tasks.get(0);
  }

  @Override
  public void approve(long taskId, long reviewerId, String comment) {
    transition(taskId, reviewerId, "APPROVED", comment);
  }

  @Override
  public void reject(long taskId, long reviewerId, String reason) {
    transition(taskId, reviewerId, "REJECTED", reason);
  }

  private void transition(long taskId, long reviewerId, String status, String comment) {
    int updated = jdbc.update("UPDATE approval_tasks SET status=:status,reviewed_by=:reviewer,"
            + "review_comment=:comment,reviewed_at=CURRENT_TIMESTAMP WHERE id=:id AND status='PENDING'",
        new MapSqlParameterSource().addValue("status", status).addValue("reviewer", reviewerId)
            .addValue("comment", comment).addValue("id", taskId));
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
