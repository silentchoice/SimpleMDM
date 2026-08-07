package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.metadata.MetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

class JdbcRecordRepositoryStateTest {
  @Test void createActivatesMasterAndChildrenWithoutWritingHistory() throws Exception {
    try (RepositoryDatabase database = RepositoryDatabase.start()) {
      JdbcRecordRepository repository = database.repository();
      var draft = repository.saveDraft(1L, 1L, new RecordDraft(0L, null, 1L, 1L, "CUS-1",
          RecordAction.CREATE, 0L, Map.of("name", "North"), List.of(
              new RecordDraft.ChildRows(1L, List.of(
                  new RecordDraft.ChildRow(null, 0, Map.of("contact", "Li"))))),
          RecordStatus.DRAFT, 1L, null));
      database.pending(draft.id());

      RecordView activated = repository.activate(draft.id(), 1L);

      assertThat(activated).extracting(RecordView::version, RecordView::status)
          .containsExactly(1L, "ACTIVE");
      assertThat(activated.children().get(0).rows()).hasSize(1);
      assertThat(database.historyVersions(activated.id())).isEmpty();
    }
  }

  @Test void updatesSnapshotTheOldActiveMasterAndChildrenAndRetainVersionsThreeTwoOne()
      throws Exception {
    try (RepositoryDatabase database = RepositoryDatabase.start()) {
      JdbcRecordRepository repository = database.repository();
      long recordId = database.activeRecord("North", "Li");
      for (long baseVersion = 1; baseVersion <= 3; baseVersion++) {
        RecordView current = repository.findRecord(1L, recordId);
        var draft = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L, "CUS-1",
            RecordAction.UPDATE, baseVersion, Map.of("name", "North " + (baseVersion + 1)),
            List.of(new RecordDraft.ChildRows(1L, List.of(new RecordDraft.ChildRow(
                current.children().get(0).rows().get(0).id(), 0,
                Map.of("contact", "Li " + (baseVersion + 1)))))), RecordStatus.DRAFT, 1L, null));
        database.pending(draft.id());
        repository.activate(draft.id(), 1L);
      }

      assertThat(database.historyVersions(recordId)).containsExactly(3L, 2L, 1L);
      var oldSnapshot = database.historySnapshot(recordId, 1L);
      assertThat(oldSnapshot.masterValues()).containsEntry("name", "North");
      assertThat(oldSnapshot.children().get(0).rows().get(0).values())
          .containsEntry("contact", "Li");
    }
  }

  @Test void deleteSnapshotsTheOldActiveMasterAndChildren() throws Exception {
    try (RepositoryDatabase database = RepositoryDatabase.start()) {
      JdbcRecordRepository repository = database.repository();
      long recordId = database.activeRecord("North", "Li");
      RecordView before = repository.findRecord(1L, recordId);
      var deletion = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L, "CUS-1",
          RecordAction.DELETE, 1L, before.masterValues(), List.of(), RecordStatus.DRAFT,
          1L, "Duplicate"));
      database.pending(deletion.id());

      RecordView deleted = repository.activate(deletion.id(), 1L);

      assertThat(deleted.status()).isEqualTo("DELETED");
      assertThat(database.historyVersions(recordId)).containsExactly(1L);
      var oldSnapshot = database.historySnapshot(recordId, 1L);
      assertThat(oldSnapshot.masterValues()).containsEntry("name", "North");
      assertThat(oldSnapshot.children().get(0).rows().get(0).values())
          .containsEntry("contact", "Li");
      assertThat(database.historyStatus(recordId, 1L)).isEqualTo("ACTIVE");
    }
  }

  @Test void repeatedDesiredStateChildDeletionKeepsTheOriginalDeletedAt() throws Exception {
    try (RepositoryDatabase database = RepositoryDatabase.start()) {
      JdbcRecordRepository repository = database.repository();
      long recordId = database.activeRecord("North", "Li");
      long childId = repository.findRecord(1L, recordId).children().get(0).rows().get(0).id();
      var update = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L, "CUS-1",
          RecordAction.UPDATE, 1L, Map.of("name", "North 2"), List.of(), RecordStatus.DRAFT,
          1L, null));
      database.pending(update.id());
      repository.activate(update.id(), 1L);
      String deletedAt = database.childDeletedAt(childId);
      var secondUpdate = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L,
          "CUS-1", RecordAction.UPDATE, 2L, Map.of("name", "North 3"), List.of(),
          RecordStatus.DRAFT, 1L, null));
      database.pending(secondUpdate.id());
      repository.activate(secondUpdate.id(), 1L);

      assertThat(database.childDeletedAt(childId)).isEqualTo(deletedAt);
    }
  }

  @Test void approvalCannotReviveOrDeleteAnInactiveFormalRecord() throws Exception {
    try (RepositoryDatabase database = RepositoryDatabase.start()) {
      JdbcRecordRepository repository = database.repository();
      long recordId = database.activeRecord("North", "Li");
      database.deactivate(recordId);
      var update = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L, "CUS-1",
          RecordAction.UPDATE, 1L, Map.of("name", "Revived"), List.of(), RecordStatus.DRAFT,
          1L, null));
      database.pending(update.id());

      assertThatThrownBy(() -> repository.activate(update.id(), 1L))
          .isInstanceOf(com.example.mdm.common.error.BusinessException.class);
      assertThat(database.recordStatus(recordId)).isEqualTo("DELETED");
    }
  }

  @Test void deletedRecordQueriesReturnOnlyTheChildrenRetainedAtDeletion() throws Exception {
    try (RepositoryDatabase database = RepositoryDatabase.start()) {
      JdbcRecordRepository repository = database.repository();
      long recordId = database.activeRecord("North", "Old child");
      var update = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L,
          "CUS-1", RecordAction.UPDATE, 1L, Map.of("name", "North 2"),
          List.of(new RecordDraft.ChildRows(1L, List.of(
              new RecordDraft.ChildRow(null, 0, Map.of("contact", "Retained child"))))),
          RecordStatus.DRAFT, 1L, null));
      database.pending(update.id());
      RecordView current = repository.activate(update.id(), 1L);
      var deletion = repository.saveDraft(1L, 1L, new RecordDraft(0L, recordId, 1L, 1L,
          "CUS-1", RecordAction.DELETE, 2L, current.masterValues(), List.of(),
          RecordStatus.DRAFT, 1L, "Duplicate"));
      database.pending(deletion.id());
      repository.activate(deletion.id(), 1L);

      RecordQueryService queries = database.queryService();
      RecordView detail = queries.detail(recordId);
      var page = queries.list(new RecordQueryService.RecordQuery(1L, null, null, null, true,
          0, 20, "id", "asc"));

      assertThat(detail.status()).isEqualTo("DELETED");
      assertThat(detail.children()).singleElement().satisfies(group ->
          assertThat(group.rows()).singleElement().satisfies(row ->
              assertThat(row.values()).containsEntry("contact", "Retained child")));
      assertThat(page.content()).singleElement().satisfies(record ->
          assertThat(record.children().get(0).rows()).hasSize(1));
    }
  }

  private static final class RepositoryDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final MySQLContainer<?> container;
    private final String schema = "mdm_task2_" + UUID.randomUUID().toString().replace("-", "");
    private final ObjectMapper json = new ObjectMapper();

    private RepositoryDatabase(String serverUrl, String username, String password,
        MySQLContainer<?> container) {
      this.serverUrl = serverUrl;
      this.username = username;
      this.password = password;
      this.container = container;
    }

    static RepositoryDatabase start() throws Exception {
      WorkflowTestEnvironment.MySqlSettings local = WorkflowTestEnvironment.mysql();
      RepositoryDatabase database = local == null ? containerDatabase()
          : new RepositoryDatabase(local.serverUrl(), local.username(), local.password(), null);
      database.createAndMigrate();
      return database;
    }

    private static RepositoryDatabase containerDatabase() {
      var container = new MySQLContainer<>("mysql:8.0.36");
      container.start();
      return new RepositoryDatabase(container.getJdbcUrl(), "root", container.getPassword(), container);
    }

    private void createAndMigrate() throws Exception {
      try (var connection = DriverManager.getConnection(serverUrl, username, password);
          var statement = connection.createStatement()) {
        assertThat(statement.executeQuery("SELECT VERSION()").next()).isTrue();
        statement.execute("CREATE DATABASE `" + schema + "`");
      }
      Flyway.configure().dataSource(schemaUrl(), username, password).locations("classpath:db/migration")
          .load().migrate();
      jdbc().getJdbcTemplate().update("INSERT INTO departments(code,name,status) VALUES('D1','Department 1','ACTIVE')");
      jdbc().getJdbcTemplate().update("INSERT INTO users(username,password_hash,display_name,status) VALUES('editor','x','Editor','ACTIVE')");
      jdbc().getJdbcTemplate().update("INSERT INTO master_types(code,name,status,created_by) VALUES('CUS','Customer','ACTIVE',1)");
      jdbc().getJdbcTemplate().update("INSERT INTO sub_types(master_type_id,department_id,code,name,status,sort_order) VALUES(1,1,'CONTACT','Contact','ACTIVE',0)");
    }

    JdbcRecordRepository repository() {
      return new JdbcRecordRepository(jdbc(), json, new RecordSnapshotCodec(json));
    }

    RecordQueryService queryService() {
      var authorization = Mockito.mock(AuthorizationService.class);
      when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
          Role.DEPT_VIEWER)).thenReturn(new UserPrincipal(1L, "editor", "Editor",
              new DepartmentPrincipal(1L, "D1", "Department 1"), List.of(Role.DEPT_EDITOR)));
      var visibility = new RecordVisibilityService(Mockito.mock(MetadataRepository.class));
      return new RecordQueryService(jdbc(), json, new RecordSnapshotCodec(json), visibility,
          authorization);
    }

    long activeRecord(String name, String contact) {
      var template = jdbc();
      template.getJdbcTemplate().update("INSERT INTO master_records(master_type_id,department_id,record_code,field_values,version,status,created_by) VALUES(1,1,'CUS-1',?,1,'ACTIVE',1)",
          "{\"name\":\"" + name + "\"}");
      Long recordId = template.getJdbcTemplate().queryForObject("SELECT id FROM master_records WHERE record_code='CUS-1'", Long.class);
      template.getJdbcTemplate().update("INSERT INTO sub_records(master_record_id,sub_type_id,row_order,field_values,version,status,created_by) VALUES(?,1,0,?,1,'ACTIVE',1)",
          recordId, "{\"contact\":\"" + contact + "\"}");
      return recordId;
    }

    void pending(long draftId) {
      jdbc().getJdbcTemplate().update("UPDATE master_record_drafts SET status='PENDING' WHERE id=?", draftId);
    }

    void deactivate(long recordId) {
      jdbc().getJdbcTemplate().update("UPDATE master_records SET status='DELETED',deleted_at=CURRENT_TIMESTAMP WHERE id=?", recordId);
    }

    String recordStatus(long recordId) {
      return jdbc().getJdbcTemplate().queryForObject("SELECT status FROM master_records WHERE id=?", String.class, recordId);
    }

    List<Long> historyVersions(long recordId) {
      return jdbc().getJdbcTemplate().queryForList("SELECT version FROM master_record_history WHERE master_record_id=? ORDER BY version DESC", Long.class, recordId);
    }

    RecordSnapshotCodec.Snapshot historySnapshot(long recordId, long version) {
      String snapshot = jdbc().getJdbcTemplate().queryForObject("SELECT snapshot FROM master_record_history WHERE master_record_id=? AND version=?", String.class, recordId, version);
      return new RecordSnapshotCodec(json).decode(snapshot);
    }

    String historyStatus(long recordId, long version) {
      return jdbc().getJdbcTemplate().queryForObject("SELECT status FROM master_record_history WHERE master_record_id=? AND version=?", String.class, recordId, version);
    }

    String childDeletedAt(long childId) {
      return jdbc().getJdbcTemplate().queryForObject("SELECT DATE_FORMAT(deleted_at,'%Y-%m-%d %H:%i:%s.%f') FROM sub_records WHERE id=?", String.class, childId);
    }

    private NamedParameterJdbcTemplate jdbc() {
      return new NamedParameterJdbcTemplate(new DriverManagerDataSource(schemaUrl(), username, password));
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
              if (schema.matches("mdm_task2_[0-9a-f]{32}")) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
              }
            }
          },
          () -> { if (container != null) container.stop(); });
    }
  }
}
