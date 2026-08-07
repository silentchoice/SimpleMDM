package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

class DashboardSqlTest {
  @SuppressWarnings("unchecked")
  @Test void activatedThisMonthSqlExcludesApprovedDeleteRequests() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
    var authorization = Mockito.mock(AuthorizationService.class);
    when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
        Role.DEPT_VIEWER)).thenReturn(new UserPrincipal(2, "approver", "Approver",
            new DepartmentPrincipal(7, "D7", "Department 7"), List.of(Role.DEPT_APPROVER)));
    @SuppressWarnings("unchecked") ObjectProvider<Clock> clocks = Mockito.mock(ObjectProvider.class);
    when(clocks.getIfAvailable(any())).thenReturn(
        Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC));

    new DashboardService(jdbc, authorization, clocks).summary();

    var sql = ArgumentCaptor.forClass(String.class);
    Mockito.verify(jdbc, Mockito.times(3)).queryForObject(sql.capture(),
        any(MapSqlParameterSource.class), eq(Long.class));
    assertThat(sql.getAllValues()).anySatisfy(value -> assertThat(value)
        .contains("master_record_drafts", "record_action<>'DELETE'"));
  }

  @SuppressWarnings("unchecked")
  @Test void realMySqlActivatedCountIncludesCreateButExcludesDelete() throws Exception {
    try (DashboardDatabase database = DashboardDatabase.start()) {
      var authorization = Mockito.mock(AuthorizationService.class);
      when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
          Role.DEPT_VIEWER)).thenReturn(new UserPrincipal(2, "approver", "Approver",
              new DepartmentPrincipal(1, "D1", "Department 1"), List.of(Role.DEPT_APPROVER)));
      @SuppressWarnings("unchecked") ObjectProvider<Clock> clocks = Mockito.mock(ObjectProvider.class);
      when(clocks.getIfAvailable(any())).thenReturn(
          Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC));

      DashboardService.DashboardSummary summary =
          new DashboardService(database.jdbc, authorization, clocks).summary();

      assertThat(summary.activatedThisMonth()).isEqualTo(1);
    }
  }

  private static final class DashboardDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final MySQLContainer<?> container;
    private final String schema = "mdm_task6_" + UUID.randomUUID().toString().replace("-", "");
    private NamedParameterJdbcTemplate jdbc;

    private DashboardDatabase(String serverUrl, String username, String password,
        MySQLContainer<?> container) {
      this.serverUrl = serverUrl;
      this.username = username;
      this.password = password;
      this.container = container;
    }

    static DashboardDatabase start() throws Exception {
      WorkflowTestEnvironment.MySqlSettings local = WorkflowTestEnvironment.mysql();
      DashboardDatabase database;
      if (local == null) {
        var container = new MySQLContainer<>("mysql:8.0.36");
        container.start();
        database = new DashboardDatabase(container.getJdbcUrl(), "root", container.getPassword(),
            container);
      } else {
        database = new DashboardDatabase(local.serverUrl(), local.username(), local.password(), null);
      }
      try {
        database.initialize();
        return database;
      } catch (Exception failure) {
        throw WorkflowTestEnvironment.withCleanupFailure(failure, database::close);
      }
    }

    private void initialize() throws Exception {
      try (var connection = DriverManager.getConnection(serverUrl, username, password);
          var statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE `" + schema + "`");
      }
      Flyway.configure().dataSource(schemaUrl(), username, password)
          .locations("classpath:db/migration").load().migrate();
      jdbc = new NamedParameterJdbcTemplate(
          new DriverManagerDataSource(schemaUrl(), username, password));
      var sql = jdbc.getJdbcTemplate();
      sql.update("INSERT INTO departments(id,code,name,status) VALUES(1,'D1','Department 1','ACTIVE')");
      sql.update("INSERT INTO users(id,username,password_hash,display_name,department_id,status) "
          + "VALUES(1,'editor','x','Editor',1,'ACTIVE'),"
          + "(2,'approver','x','Approver',1,'ACTIVE')");
      sql.update("INSERT INTO master_types(id,code,name,status,created_by) "
          + "VALUES(1,'CUS','Customer','ACTIVE',1)");
      sql.update("INSERT INTO master_records(id,master_type_id,department_id,record_code,"
          + "field_values,status,created_by) VALUES(21,1,1,'DEL-1','{}','ACTIVE',1)");
      sql.update("INSERT INTO master_record_drafts(id,master_record_id,master_type_id,"
          + "department_id,record_code,record_action,field_values,base_version,status,created_by) "
          + "VALUES(11,NULL,1,1,'NEW-1','CREATE','{}',0,'APPROVED',1),"
          + "(12,21,1,1,'DEL-1','DELETE','{}',1,'APPROVED',1)");
      sql.update("INSERT INTO approval_tasks(id,department_id,entity_type,entity_id,"
          + "after_snapshot,status,submitted_by,reviewed_by,submitted_at,reviewed_at) VALUES"
          + "(31,1,'RECORD',11,'{}','APPROVED',1,2,'2026-08-05 09:00:00',"
          + "'2026-08-05 10:00:00'),"
          + "(32,1,'RECORD',12,'{}','APPROVED',1,2,'2026-08-05 11:00:00',"
          + "'2026-08-05 12:00:00')");
    }

    private String schemaUrl() {
      int query = serverUrl.indexOf('?');
      String parameters = query < 0 ? "" : serverUrl.substring(query);
      String base = query < 0 ? serverUrl : serverUrl.substring(0, query);
      int pathStart = base.indexOf('/', base.indexOf("://") + 3);
      return (pathStart < 0 ? base : base.substring(0, pathStart)) + "/" + schema + parameters;
    }

    @Override public void close() throws Exception {
      WorkflowTestEnvironment.cleanup(
          () -> {
            try (var connection = DriverManager.getConnection(serverUrl, username, password);
                var statement = connection.createStatement()) {
              if (schema.matches("mdm_task6_[0-9a-f]{32}")) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
              }
            }
          },
          () -> { if (container != null) container.stop(); });
    }
  }
}
