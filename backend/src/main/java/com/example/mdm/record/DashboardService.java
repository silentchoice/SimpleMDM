package com.example.mdm.record;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private static final int RECENT_LIMIT = 5;
  private final DashboardStore store;
  private final AuthorizationService authorization;
  private final Clock clock;

  public DashboardService(DashboardStore store, AuthorizationService authorization, Clock clock) {
    this.store = store;
    this.authorization = authorization;
    this.clock = clock;
  }

  @Autowired
  DashboardService(NamedParameterJdbcTemplate jdbc, AuthorizationService authorization,
      ObjectProvider<Clock> clocks) {
    this(new JdbcDashboardStore(jdbc), authorization, clocks.getIfAvailable(Clock::systemUTC));
  }

  public DashboardSummary summary() {
    UserPrincipal actor = authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR,
        Role.DEPT_APPROVER, Role.DEPT_VIEWER);
    boolean admin = actor.roles().contains(Role.SUPER_ADMIN);
    Long departmentId = admin ? null : department(actor);
    long drafts = !admin && actor.roles().contains(Role.DEPT_EDITOR)
        ? store.draftCount(departmentId, actor.id()) : 0;
    long pending = admin || actor.roles().contains(Role.DEPT_APPROVER)
        ? store.pendingApprovalCount(departmentId) : 0;
    Long submittedBy = !admin && !actor.roles().contains(Role.DEPT_APPROVER)
        && actor.roles().contains(Role.DEPT_EDITOR) ? actor.id() : null;
    List<RecentTask> recent = admin || actor.roles().contains(Role.DEPT_APPROVER)
        || actor.roles().contains(Role.DEPT_EDITOR)
        ? store.recentTasks(departmentId, submittedBy, RECENT_LIMIT) : List.of();
    LocalDateTime monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
    return new DashboardSummary(store.formalCount(departmentId), drafts, pending,
        store.activatedSince(departmentId, monthStart), recent);
  }

  private long department(UserPrincipal actor) {
    if (actor.department() == null) throw BusinessException.forbidden();
    authorization.requireDepartment(actor.department().id());
    return actor.department().id();
  }

  public record DashboardSummary(long formalCount, long myDraftCount, long pendingApprovalCount,
      long activatedThisMonth, List<RecentTask> recentTasks) {
    public DashboardSummary { recentTasks = List.copyOf(recentTasks); }
  }

  public record RecentTask(long id, String taskType, String entityKind, long entityId,
      String status, long submittedBy, LocalDateTime submittedAt) {}

  interface DashboardStore {
    long formalCount(Long departmentId);
    long draftCount(long departmentId, long userId);
    long pendingApprovalCount(Long departmentId);
    long activatedSince(Long departmentId, LocalDateTime start);
    List<RecentTask> recentTasks(Long departmentId, Long submittedBy, int limit);
  }

  private static final class JdbcDashboardStore implements DashboardStore {
    private final NamedParameterJdbcTemplate jdbc;

    private JdbcDashboardStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public long formalCount(Long departmentId) {
      return count("SELECT COUNT(*) FROM master_records WHERE status='ACTIVE'"
          + departmentClause(departmentId), departmentParameters(departmentId));
    }

    @Override public long draftCount(long departmentId, long userId) {
      return count("SELECT COUNT(*) FROM master_record_drafts WHERE department_id=:department "
              + "AND created_by=:user AND status='DRAFT'",
          new MapSqlParameterSource().addValue("department", departmentId).addValue("user", userId));
    }

    @Override public long pendingApprovalCount(Long departmentId) {
      return count("SELECT COUNT(*) FROM approval_tasks WHERE status='PENDING'"
          + departmentClause(departmentId), departmentParameters(departmentId));
    }

    @Override public long activatedSince(Long departmentId, LocalDateTime start) {
      MapSqlParameterSource parameters = departmentParameters(departmentId).addValue("start", start);
      return count("SELECT COUNT(*) FROM approval_tasks WHERE entity_type='RECORD' "
          + "AND status='APPROVED' AND reviewed_at>=:start" + departmentClause(departmentId),
          parameters);
    }

    @Override public List<RecentTask> recentTasks(Long departmentId, Long submittedBy, int limit) {
      String sql = "SELECT id,entity_type,entity_id,status,submitted_by,submitted_at "
          + "FROM approval_tasks WHERE 1=1" + departmentClause(departmentId)
          + (submittedBy == null ? "" : " AND submitted_by=:submitter")
          + " ORDER BY submitted_at DESC,id DESC LIMIT :limit";
      var parameters = departmentParameters(departmentId).addValue("limit", limit);
      if (submittedBy != null) parameters.addValue("submitter", submittedBy);
      return jdbc.query(sql, parameters, (result, row) -> {
        String kind = result.getString("entity_type");
        return new RecentTask(result.getLong("id"), "RECORD".equals(kind) ? "RECORD" : "METADATA",
            kind, result.getLong("entity_id"), result.getString("status"),
            result.getLong("submitted_by"), result.getObject("submitted_at", LocalDateTime.class));
      });
    }

    private long count(String sql, MapSqlParameterSource parameters) {
      Long count = jdbc.queryForObject(sql, parameters, Long.class);
      return count == null ? 0 : count;
    }

    private String departmentClause(Long departmentId) {
      return departmentId == null ? "" : " AND department_id=:department";
    }

    private MapSqlParameterSource departmentParameters(Long departmentId) {
      var parameters = new MapSqlParameterSource();
      if (departmentId != null) parameters.addValue("department", departmentId);
      return parameters;
    }
  }
}
