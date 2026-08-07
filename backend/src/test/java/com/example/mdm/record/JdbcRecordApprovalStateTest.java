package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

class JdbcRecordApprovalStateTest {
  @Test void submitTaskAndDraftTransitionRollBackAsOneTransaction() throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long draftId = database.createDraftForAtomicFailure();

      assertThatThrownBy(() -> database.failAfterTaskInsert(draftId))
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.getMessage()).isEqualTo("Draft is no longer editable"));

      assertThat(database.approvalTaskCount()).isZero();
      assertThat(database.rawDraftStatus(draftId)).isEqualTo("DRAFT");
    }
  }

  @Test void taskCompletionFailureRollsBackActivatedMasterChildrenHistoryAndDraft()
      throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long recordId = database.activeRecord();
      long taskId = database.submitUpdate(recordId, 1, "Rolled back");

      assertThatThrownBy(() -> database.approveWithCompletionFailure(taskId, recordId))
          .isInstanceOfSatisfying(IllegalStateException.class,
              error -> assertThat(error.getMessage()).isEqualTo("task completion failed"));

      RecordView formal = database.repository().findRecord(1, recordId);
      assertThat(formal.version()).isEqualTo(1);
      assertThat(formal.masterValues()).containsEntry("name", "North");
      assertThat(formal.children().get(0).rows().get(0).values()).containsEntry("contact", "Li");
      assertThat(database.historyVersions(recordId)).isEmpty();
      assertThat(database.rawDraftStatus(database.lastDraftId())).isEqualTo("PENDING");
      assertThat(database.approvalStatus(taskId)).isEqualTo("PENDING");
    }
  }

  @Test void duplicateSubmitAndDuplicateApproveLeaveOneTaskAndOneActivation() throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long recordId = database.activeRecord();
      long taskId = database.submitUpdate(recordId, 1, "Approved once");

      assertThatThrownBy(database::duplicateSubmit)
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.getMessage()).isEqualTo("Draft is no longer editable"));
      assertThat(database.approvalTaskCount()).isEqualTo(1);

      database.approve(taskId);
      assertThatThrownBy(() -> database.approve(taskId))
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.getMessage()).isEqualTo("Approval task is not pending"));

      assertThat(database.repository().findRecord(1, recordId).version()).isEqualTo(2);
      assertThat(database.historyVersions(recordId)).containsExactly(1L);
      assertThat(database.approvalTaskCount()).isEqualTo(1);
      assertThat(database.approvalStatus(taskId)).isEqualTo("APPROVED");
    }
  }

  @Test void foreignDepartmentCannotSubmitTheDraftOrReviewItsTask() throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long recordId = database.activeRecord();
      long draftId = database.createUnsubmittedUpdate(recordId, 1, "Foreign denied");

      assertThatThrownBy(() -> database.submitAsForeignDepartment(draftId))
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
      assertThat(database.rawDraftStatus(draftId)).isEqualTo("DRAFT");
      assertThat(database.approvalTaskCount()).isZero();

      long taskId = database.submitExistingUpdate(draftId, recordId, 1);
      assertThatThrownBy(() -> database.approveAsForeignDepartment(taskId))
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
      assertThat(database.rawDraftStatus(draftId)).isEqualTo("PENDING");
      assertThat(database.approvalStatus(taskId)).isEqualTo("PENDING");
      assertThat(database.repository().findRecord(1, recordId).version()).isEqualTo(1);
    }
  }

  @Test void updateApprovalsAreAtomicReleaseAfterCommitAndRetainOldVersionsThreeTwoOne()
      throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long recordId = database.activeRecord();
      for (long base = 1; base <= 3; base++) {
        long taskId = database.submitUpdate(recordId, base, "North " + (base + 1));
        assertThat(database.draftStatus(taskId)).isEqualTo("PENDING");
        assertThat(database.taskBinding(taskId)).containsExactly("RECORD", database.lastDraftId());
        assertThat(database.lockHeld(recordId)).isFalse();

        database.approve(taskId);
      }

      assertThat(database.repository().findRecord(1, recordId).version()).isEqualTo(4);
      assertThat(database.historyVersions(recordId)).containsExactly(3L, 2L, 1L);
      assertThat(database.taskStatuses()).containsOnly("APPROVED");
    }
  }

  @Test void rejectionDoesNotMutateFormalDataAndCannotBeRepeatedOrEditedInPlace()
      throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long recordId = database.activeRecord();
      long taskId = database.submitUpdate(recordId, 1, "Rejected name");

      database.reject(taskId);

      assertThat(database.repository().findRecord(1, recordId).masterValues())
          .containsEntry("name", "North");
      assertThat(database.repository().findRecord(1, recordId).version()).isEqualTo(1);
      assertThat(database.draftStatus(taskId)).isEqualTo("REJECTED");
      assertThatThrownBy(() -> database.reject(taskId))
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.getMessage()).isEqualTo("Approval task is not pending"));
      assertThatThrownBy(database::overwriteRejectedDraft)
          .isInstanceOfSatisfying(BusinessException.class,
              error -> assertThat(error.getMessage()).isEqualTo("Draft is no longer editable"));
    }
  }

  @Test void createAndLogicalDeleteMutateFormalRowsOnlyWhenApproved() throws Exception {
    try (ApprovalDatabase database = ApprovalDatabase.start()) {
      long createTask = database.submitCreate();
      assertThat(database.formalCount()).isZero();

      RecordView created = database.approve(createTask);
      assertThat(created).extracting(RecordView::version, RecordView::status)
          .containsExactly(1L, "ACTIVE");

      long deleteTask = database.submitDelete(created.id());
      assertThat(database.repository().findRecord(1, created.id()).status()).isEqualTo("ACTIVE");
      RecordView deleted = database.approve(deleteTask);

      assertThat(deleted.status()).isEqualTo("DELETED");
      assertThat(database.formalCount()).isEqualTo(1);
      assertThat(database.historyVersions(created.id())).containsExactly(1L);
      assertThat(database.lockHeld(created.id())).isFalse();
    }
  }

  private static final class ApprovalDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final MySQLContainer<?> container;
    private final String schema = "mdm_task4_" + UUID.randomUUID().toString().replace("-", "");
    private final DriverManagerDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcRecordRepository records;
    private final JdbcRecordApprovalRepository approvals;
    private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
    private final MapLocks locks = new MapLocks();
    private final RecordApprovalService service;
    private final TransactionTemplate transactions;
    private long lastDraftId;

    private ApprovalDatabase(String serverUrl, String username, String password,
        MySQLContainer<?> container) {
      this.serverUrl = serverUrl;
      this.username = username;
      this.password = password;
      this.container = container;
      this.dataSource = new DriverManagerDataSource(schemaUrl(), username, password);
      this.jdbc = new NamedParameterJdbcTemplate(dataSource);
      var json = new ObjectMapper();
      this.records = new JdbcRecordRepository(jdbc, json, new RecordSnapshotCodec(json));
      this.approvals = new JdbcRecordApprovalRepository(jdbc, records);
      this.service = new RecordApprovalService(approvals, records, locks, authorization,
          new RecordSnapshotCodec(json));
      this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    static ApprovalDatabase start() throws Exception {
      WorkflowTestEnvironment.MySqlSettings local = WorkflowTestEnvironment.mysql();
      ApprovalDatabase database;
      if (local == null) {
        var container = new MySQLContainer<>("mysql:8.0.36");
        container.start();
        database = new ApprovalDatabase(container.getJdbcUrl(), "root", container.getPassword(),
            container);
      } else {
        database = new ApprovalDatabase(local.serverUrl(), local.username(), local.password(), null);
      }
      database.createAndMigrate();
      return database;
    }

    private void createAndMigrate() throws Exception {
      try (var connection = DriverManager.getConnection(serverUrl, username, password);
          var statement = connection.createStatement()) {
        assertThat(statement.executeQuery("SELECT VERSION()").next()).isTrue();
        statement.execute("CREATE DATABASE `" + schema + "`");
      }
      Flyway.configure().dataSource(schemaUrl(), username, password)
          .locations("classpath:db/migration").load().migrate();
      jdbc.getJdbcTemplate().update(
          "INSERT INTO departments(code,name,status) VALUES"
              + "('D1','Department 1','ACTIVE'),('D2','Department 2','ACTIVE')");
      jdbc.getJdbcTemplate().update("INSERT INTO users(username,password_hash,display_name,status) "
          + "VALUES('editor','x','Editor','ACTIVE'),('approver','x','Approver','ACTIVE'),"
          + "('foreign-editor','x','Foreign Editor','ACTIVE'),"
          + "('foreign-approver','x','Foreign Approver','ACTIVE')");
      jdbc.getJdbcTemplate().update("INSERT INTO master_types(code,name,status,created_by) "
          + "VALUES('CUS','Customer','ACTIVE',1)");
      jdbc.getJdbcTemplate().update("INSERT INTO sub_types(master_type_id,department_id,code,name,"
          + "status,sort_order) VALUES(1,1,'CONTACT','Contact','ACTIVE',0)");
    }

    long activeRecord() {
      jdbc.getJdbcTemplate().update("INSERT INTO master_records(master_type_id,department_id,"
          + "record_code,field_values,version,status,created_by) "
          + "VALUES(1,1,'CUS-1','{\"name\":\"North\"}',1,'ACTIVE',1)");
      long id = jdbc.getJdbcTemplate().queryForObject(
          "SELECT id FROM master_records WHERE record_code='CUS-1'", Long.class);
      jdbc.getJdbcTemplate().update("INSERT INTO sub_records(master_record_id,sub_type_id,row_order,"
          + "field_values,version,status,created_by) "
          + "VALUES(?,1,0,'{\"contact\":\"Li\"}',1,'ACTIVE',1)", id);
      return id;
    }

    long createDraftForAtomicFailure() {
      return records.saveDraft(1, 1, new RecordDraft(0L, null, 1, 1, "ATOMIC-1",
          RecordAction.CREATE, 0, Map.of("name", "Atomic"), List.of(), RecordStatus.DRAFT,
          1, null)).id();
    }

    void failAfterTaskInsert(long draftId) {
      transactions.executeWithoutResult(status -> {
        long taskId = approvals.submit(1, 1, draftId, null, "{}");
        approvals.markPending(1, draftId + 1000, taskId);
      });
    }

    long submitUpdate(long recordId, long baseVersion, String name) {
      long draftId = createUnsubmittedUpdate(recordId, baseVersion, name);
      return submitExistingUpdate(draftId, recordId, baseVersion);
    }

    long createUnsubmittedUpdate(long recordId, long baseVersion, String name) {
      RecordView current = records.findRecord(1, recordId);
      long childId = current.children().get(0).rows().get(0).id();
      RecordDraft draft = records.saveDraft(1, 1, new RecordDraft(0L, recordId, 1, 1, "CUS-1",
          RecordAction.UPDATE, baseVersion, Map.of("name", name),
          List.of(new RecordDraft.ChildRows(1, List.of(new RecordDraft.ChildRow(childId, 0,
              Map.of("contact", "Li " + name))))), RecordStatus.DRAFT, 1, null));
      lastDraftId = draft.id();
      return draft.id();
    }

    long submitExistingUpdate(long draftId, long recordId, long baseVersion) {
      locks.put(new EditLock(recordId, 1, 1, "Editor", "token-" + baseVersion,
          Instant.now().plusSeconds(1800)));
      when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
      return transactions.execute(status -> service.submit(draftId, "token-" + baseVersion));
    }

    long submitCreate() {
      RecordDraft draft = records.saveDraft(1, 1, new RecordDraft(0L, null, 1, 1, "CUS-NEW",
          RecordAction.CREATE, 0, Map.of("name", "Created"), List.of(), RecordStatus.DRAFT,
          1, null));
      lastDraftId = draft.id();
      when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
      return transactions.execute(status -> service.submit(draft.id(), null));
    }

    long submitDelete(long recordId) {
      RecordView current = records.findRecord(1, recordId);
      RecordDraft draft = records.saveDraft(1, 1, new RecordDraft(0L, recordId, 1, 1,
          current.recordCode(), RecordAction.DELETE, current.version(), current.masterValues(),
          List.of(), RecordStatus.DRAFT, 1, "duplicate"));
      lastDraftId = draft.id();
      locks.put(new EditLock(recordId, 1, 1, "Editor", "delete-token",
          Instant.now().plusSeconds(1800)));
      when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
      return transactions.execute(status -> service.submit(draft.id(), "delete-token"));
    }

    RecordView approve(long taskId) {
      when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
      return transactions.execute(status -> service.approve(taskId, "approved"));
    }

    void approveWithCompletionFailure(long taskId, long recordId) {
      RecordApprovalRepository failing = Mockito.spy(approvals);
      Mockito.doAnswer(invocation -> {
        RecordView activated = records.findRecord(1, recordId);
        assertThat(activated.version()).isEqualTo(2);
        assertThat(activated.masterValues()).containsEntry("name", "Rolled back");
        assertThat(activated.children().get(0).rows().get(0).values())
            .containsEntry("contact", "Li Rolled back");
        assertThat(historyVersions(recordId)).containsExactly(1L);
        assertThat(rawDraftStatus(lastDraftId)).isEqualTo("APPROVED");
        throw new IllegalStateException("task completion failed");
      }).when(failing).approve(1, taskId, 2, "approved");
      var failingService = new RecordApprovalService(failing, records, locks, authorization,
          new RecordSnapshotCodec(new ObjectMapper()));
      when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
      transactions.executeWithoutResult(status -> failingService.approve(taskId, "approved"));
    }

    void duplicateSubmit() {
      when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
      transactions.executeWithoutResult(status -> service.submit(lastDraftId, "token-1"));
    }

    void submitAsForeignDepartment(long draftId) {
      when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(foreignEditor());
      transactions.executeWithoutResult(status -> service.submit(draftId, "foreign-token"));
    }

    void approveAsForeignDepartment(long taskId) {
      when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(foreignApprover());
      transactions.executeWithoutResult(status -> service.approve(taskId, "foreign"));
    }

    void reject(long taskId) {
      when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
      transactions.executeWithoutResult(status -> service.reject(taskId, "incorrect"));
    }

    void overwriteRejectedDraft() {
      RecordDraft rejected = records.findDraft(1, lastDraftId);
      records.saveDraft(1, 1, new RecordDraft(rejected.id(), rejected.recordId(),
          rejected.masterTypeId(), rejected.departmentId(), rejected.recordCode(), rejected.action(),
          rejected.baseVersion(), Map.of("name", "overwritten"), rejected.children(),
          rejected.status(), rejected.createdBy(), rejected.deleteReason()));
    }

    JdbcRecordRepository repository() { return records; }
    long lastDraftId() { return lastDraftId; }
    boolean lockHeld(long recordId) { return locks.find(recordId) != null; }

    String draftStatus(long taskId) {
      return jdbc.getJdbcTemplate().queryForObject("SELECT draft.status FROM master_record_drafts "
          + "draft JOIN approval_tasks task ON task.id=draft.approval_task_id WHERE task.id=?",
          String.class, taskId);
    }

    List<Object> taskBinding(long taskId) {
      return jdbc.getJdbcTemplate().queryForObject("SELECT entity_type,entity_id FROM approval_tasks "
          + "WHERE id=?", (result, row) -> List.of(result.getString(1), result.getLong(2)), taskId);
    }

    List<Long> historyVersions(long recordId) {
      return jdbc.getJdbcTemplate().queryForList("SELECT version FROM master_record_history "
          + "WHERE master_record_id=? ORDER BY version DESC", Long.class, recordId);
    }

    List<String> taskStatuses() {
      return jdbc.getJdbcTemplate().queryForList("SELECT status FROM approval_tasks", String.class);
    }

    int formalCount() {
      return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM master_records",
          Integer.class);
    }

    int approvalTaskCount() {
      return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM approval_tasks",
          Integer.class);
    }

    String rawDraftStatus(long draftId) {
      return jdbc.getJdbcTemplate().queryForObject(
          "SELECT status FROM master_record_drafts WHERE id=?", String.class, draftId);
    }

    String approvalStatus(long taskId) {
      return jdbc.getJdbcTemplate().queryForObject(
          "SELECT status FROM approval_tasks WHERE id=?", String.class, taskId);
    }

    private UserPrincipal editor() {
      return new UserPrincipal(1, "editor", "Editor", department(), List.of(Role.DEPT_EDITOR));
    }

    private UserPrincipal approver() {
      return new UserPrincipal(2, "approver", "Approver", department(), List.of(Role.DEPT_APPROVER));
    }

    private DepartmentPrincipal department() {
      return new DepartmentPrincipal(1, "D1", "Department 1");
    }

    private UserPrincipal foreignEditor() {
      return new UserPrincipal(3, "foreign-editor", "Foreign Editor", foreignDepartment(),
          List.of(Role.DEPT_EDITOR));
    }

    private UserPrincipal foreignApprover() {
      return new UserPrincipal(4, "foreign-approver", "Foreign Approver", foreignDepartment(),
          List.of(Role.DEPT_APPROVER));
    }

    private DepartmentPrincipal foreignDepartment() {
      return new DepartmentPrincipal(2, "D2", "Department 2");
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
              if (schema.matches("mdm_task4_[0-9a-f]{32}")) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
              }
            }
          },
          () -> { if (container != null) container.stop(); });
    }
  }

  private static final class MapLocks implements EditLockStore {
    private final Map<Long, EditLock> values = new HashMap<>();
    void put(EditLock lock) { values.put(lock.recordId(), lock); }
    @Override public EditLock find(long recordId) { return values.get(recordId); }
    @Override public boolean acquire(EditLock lock, Duration ttl) {
      return values.putIfAbsent(lock.recordId(), lock) == null;
    }
    @Override public boolean renew(long recordId, String token, EditLock replacement, Duration ttl) {
      EditLock current = values.get(recordId);
      if (current == null || !current.token().equals(token)) return false;
      values.put(recordId, replacement);
      return true;
    }
    @Override public boolean release(long recordId, String token) {
      EditLock current = values.get(recordId);
      return current != null && current.token().equals(token) && values.remove(recordId, current);
    }
  }
}
