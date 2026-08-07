package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.example.mdm.metadata.FieldDefinition;
import com.example.mdm.metadata.FieldType;
import com.example.mdm.metadata.FieldValueValidator;
import com.example.mdm.metadata.MasterType;
import com.example.mdm.metadata.MetadataRepository;
import com.example.mdm.metadata.MetadataStatus;
import com.example.mdm.metadata.SubType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import redis.embedded.RedisServer;

class RecordWorkflowIntegrationTest {
  @Test void editorCreateSubmitApprovalPublishesAllRowsAndForeignReadersSeeOnlySharedFields()
      throws Exception {
    try (WorkflowHarness workflow = WorkflowHarness.start()) {
      RecordDraft draft = workflow.createRecordDraft("North", "internal-master",
          List.of(
              new RecordDraftCommand.ChildRows(WorkflowHarness.CONTACT_TYPE_ID, List.of(
                  new RecordDraftCommand.ChildRowCommand(null, 0,
                      Map.of("phone", "1001", "privateNote", "first-private")),
                  new RecordDraftCommand.ChildRowCommand(null, 1,
                      Map.of("phone", "1002", "privateNote", "second-private")))),
              new RecordDraftCommand.ChildRows(WorkflowHarness.ADDRESS_TYPE_ID, List.of(
                  new RecordDraftCommand.ChildRowCommand(null, 0,
                      Map.of("city", "Shanghai", "doorCode", "secret-door"))))));

      long taskId = workflow.submit(draft.id(), null);
      RecordView approved = workflow.approve(taskId);

      assertThat(approved.status()).isEqualTo("ACTIVE");
      assertThat(approved.version()).isEqualTo(1);
      assertThat(approved.children()).extracting(RecordView.ChildRows::subTypeId)
          .containsExactly(WorkflowHarness.CONTACT_TYPE_ID, WorkflowHarness.ADDRESS_TYPE_ID);
      assertThat(approved.children().get(0).rows()).hasSize(2);
      assertThat(workflow.detailAs(workflow.departmentViewer(), approved.id()))
          .isEqualTo(approved);

      RecordView foreign = workflow.detailAs(workflow.foreignViewer(), approved.id());
      assertThat(foreign.masterValues()).containsExactlyEntriesOf(Map.of("name", "North"));
      assertThat(foreign.children().get(0).rows()).extracting(RecordView.ChildRow::values)
          .containsExactly(Map.of("phone", "1001"), Map.of("phone", "1002"));
      assertThat(foreign.children().get(1).rows()).singleElement().satisfies(row ->
          assertThat(row.values()).containsExactlyEntriesOf(Map.of("city", "Shanghai")));
    }
  }

  @Test void approvedUpdateThenApprovedDeleteChangesFormalVisibilityWithoutPhysicalDeletion()
      throws Exception {
    try (WorkflowHarness workflow = WorkflowHarness.start()) {
      RecordView created = workflow.createAndApprove("Original");
      RecordDraft update = workflow.updateDraft(created, "Updated 2");
      String updateToken = workflow.acquireAs(workflow.editor(), created.id()).token();

      RecordView updated = workflow.approve(workflow.submit(update.id(), updateToken));

      assertThat(updated.version()).isEqualTo(2);
      assertThat(updated.masterValues()).containsEntry("name", "Updated 2");
      RecordDraft deletion = workflow.logicalDelete(updated.id(), "duplicate entry");
      String deleteToken = workflow.acquireAs(workflow.editor(), updated.id()).token();

      RecordView deleted = workflow.approve(workflow.submit(deletion.id(), deleteToken));

      assertThat(deleted.status()).isEqualTo("DELETED");
      assertThat(deleted.version()).isEqualTo(3);
      assertThat(workflow.detailAs(workflow.departmentViewer(), deleted.id()).status())
          .isEqualTo("DELETED");
      assertThat(workflow.formalRowCount(deleted.id())).isEqualTo(1);
      assertThat(workflow.visibleActiveCount()).isZero();
    }
  }

  @Test void fourApprovedUpdatesRetainOnlyTheThreeNewestPreviousVersions() throws Exception {
    try (WorkflowHarness workflow = WorkflowHarness.start()) {
      RecordView current = workflow.createAndApprove("Version 1");
      for (int nextVersion = 2; nextVersion <= 5; nextVersion++) {
        RecordDraft update = workflow.updateDraft(current, "Version " + nextVersion);
        String token = workflow.acquireAs(workflow.editor(), current.id()).token();
        current = workflow.approve(workflow.submit(update.id(), token));
      }

      assertThat(current.version()).isEqualTo(5);
      assertThat(workflow.historyAs(workflow.departmentViewer(), current.id()))
          .extracting(RecordView::version).containsExactly(4L, 3L, 2L);
      assertThat(workflow.historyRowCount(current.id())).isEqualTo(3);
    }
  }
}

final class WorkflowHarness implements AutoCloseable {
  static final long MASTER_TYPE_ID = 1L;
  static final long CONTACT_TYPE_ID = 1L;
  static final long ADDRESS_TYPE_ID = 2L;

  private final String serverUrl;
  private final String username;
  private final String password;
  private final MySQLContainer<?> container;
  private final String schema = "mdm_task9_" + UUID.randomUUID().toString().replace("-", "");
  private final AtomicReference<UserPrincipal> actor = new AtomicReference<>();
  private final ObjectMapper json = new ObjectMapper();
  private DriverManagerDataSource dataSource;
  private NamedParameterJdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private JdbcRecordRepository records;
  private RecordDraftService drafts;
  private RecordApprovalService approvals;
  private RecordQueryService queries;
  private EditLockService editLocks;
  private RedisServer redisServer;
  private LettuceConnectionFactory redisConnections;
  private StringRedisTemplate redis;
  private Path redisExecutable;

  private WorkflowHarness(String serverUrl, String username, String password,
      MySQLContainer<?> container) {
    this.serverUrl = serverUrl;
    this.username = username;
    this.password = password;
    this.container = container;
  }

  static WorkflowHarness start() throws Exception {
    String localUrl = System.getProperty("record.repository.mysql.server-url");
    WorkflowHarness harness = localUrl == null ? containerDatabase()
        : new WorkflowHarness(localUrl,
            System.getProperty("record.repository.mysql.username", "root"),
            requiredProperty("record.repository.mysql.password"), null);
    try {
      harness.initialize();
      return harness;
    } catch (Exception failure) {
      harness.close();
      throw failure;
    }
  }

  private static WorkflowHarness containerDatabase() {
    var container = new MySQLContainer<>("mysql:8.0.36");
    container.start();
    return new WorkflowHarness(container.getJdbcUrl(), "root", container.getPassword(), container);
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
    return value;
  }

  private void initialize() throws Exception {
    createSchema();
    dataSource = new DriverManagerDataSource(schemaUrl(), username, password);
    jdbc = new NamedParameterJdbcTemplate(dataSource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    seedDatabase();
    startRedis();

    AuthorizationService authorization = authorization();
    MetadataRepository metadata = metadata();
    var snapshots = new RecordSnapshotCodec(json);
    records = new JdbcRecordRepository(jdbc, json, snapshots);
    var lockStore = new RedisEditLockStore(redis);
    drafts = new RecordDraftService(records, metadata, new FieldValueValidator(),
        new CodeRuleService(new JdbcCodeSequenceRepository(jdbc), new CodeRuleParser(),
            authorization, Clock.systemUTC()), authorization, Clock.systemUTC());
    approvals = new RecordApprovalService(new JdbcRecordApprovalRepository(jdbc, records), records,
        lockStore, authorization, snapshots);
    editLocks = new EditLockService(records, lockStore, authorization, Clock.systemUTC());
    queries = new RecordQueryService(jdbc, json, snapshots, new RecordVisibilityService(metadata),
        authorization);
    as(editor());
  }

  RecordDraft createRecordDraft(String name, String internalNote,
      List<RecordDraftCommand.ChildRows> children) {
    as(editor());
    return inTransaction(() -> drafts.create(new RecordDraftCommand(null, MASTER_TYPE_ID, 0,
        RecordAction.CREATE, Map.of("name", name, "internalNote", internalNote), children, null)));
  }

  RecordView createAndApprove(String name) {
    RecordDraft draft = createRecordDraft(name, "private", List.of(
        new RecordDraftCommand.ChildRows(CONTACT_TYPE_ID, List.of(
            new RecordDraftCommand.ChildRowCommand(null, 0,
                Map.of("phone", "1000", "privateNote", "private"))))));
    return approve(submit(draft.id(), null));
  }

  RecordDraft updateDraft(RecordView formal, String name) {
    as(editor());
    List<RecordDraftCommand.ChildRows> children = formal.children().stream().map(group ->
        new RecordDraftCommand.ChildRows(group.subTypeId(), group.rows().stream().map(row ->
            new RecordDraftCommand.ChildRowCommand(row.id(), row.rowOrder(), row.values()))
            .toList())).toList();
    return inTransaction(() -> drafts.create(new RecordDraftCommand(formal.id(),
        formal.masterTypeId(), formal.version(), RecordAction.UPDATE,
        Map.of("name", name, "internalNote", "private-" + name), children, null)));
  }

  RecordDraft logicalDelete(long recordId, String reason) {
    as(editor());
    return inTransaction(() -> drafts.logicalDelete(recordId, reason));
  }

  long submit(long draftId, String token) {
    as(editor());
    return inTransaction(() -> approvals.submit(draftId, token));
  }

  RecordView approve(long taskId) {
    as(approver());
    return inTransaction(() -> approvals.approve(taskId, "accepted"));
  }

  EditLock acquireAs(UserPrincipal principal, long recordId) {
    as(principal);
    return editLocks.acquire(recordId);
  }

  RecordView detailAs(UserPrincipal principal, long recordId) {
    as(principal);
    return queries.detail(recordId);
  }

  List<RecordView> historyAs(UserPrincipal principal, long recordId) {
    as(principal);
    return queries.history(recordId);
  }

  RecordDraft createUpdateAs(UserPrincipal principal, RecordView formal, String name) {
    as(principal);
    return inTransaction(() -> drafts.create(new RecordDraftCommand(formal.id(),
        formal.masterTypeId(), formal.version(), RecordAction.UPDATE,
        Map.of("name", name, "internalNote", "private"), childCommands(formal), null)));
  }

  long submitAs(UserPrincipal principal, long draftId, String token) {
    as(principal);
    return inTransaction(() -> approvals.submit(draftId, token));
  }

  RecordView approveAs(UserPrincipal principal, long taskId) {
    as(principal);
    return inTransaction(() -> approvals.approve(taskId, "accepted"));
  }

  void advanceFormalVersion(long recordId) {
    jdbc.getJdbcTemplate().update("UPDATE master_records SET version=version+1 WHERE id=?", recordId);
  }

  RecordDraft storedDraft(long draftId) {
    return records.findDraft(1L, draftId);
  }

  EditLock storedLock(long recordId) {
    return new RedisEditLockStore(redis).find(recordId);
  }

  int formalRowCount(long recordId) {
    return jdbc.getJdbcTemplate().queryForObject(
        "SELECT COUNT(*) FROM master_records WHERE id=?", Integer.class, recordId);
  }

  int visibleActiveCount() {
    return jdbc.getJdbcTemplate().queryForObject(
        "SELECT COUNT(*) FROM master_records WHERE status='ACTIVE'", Integer.class);
  }

  int historyRowCount(long recordId) {
    return jdbc.getJdbcTemplate().queryForObject(
        "SELECT COUNT(*) FROM master_record_history WHERE master_record_id=?", Integer.class,
        recordId);
  }

  UserPrincipal editor() {
    return principal(1L, "editor", "Editor One", 1L, "D1", Role.DEPT_EDITOR);
  }

  UserPrincipal approver() {
    return principal(2L, "approver", "Approver", 1L, "D1", Role.DEPT_APPROVER);
  }

  UserPrincipal secondEditor() {
    return principal(3L, "editor2", "Editor Two", 1L, "D1", Role.DEPT_EDITOR);
  }

  UserPrincipal departmentViewer() {
    return principal(4L, "viewer", "Viewer", 1L, "D1", Role.DEPT_VIEWER);
  }

  UserPrincipal foreignViewer() {
    return principal(5L, "foreign", "Foreign Viewer", 2L, "D2", Role.DEPT_VIEWER);
  }

  UserPrincipal selfApprover() {
    return principal(1L, "editor", "Editor One", 1L, "D1", Role.DEPT_APPROVER);
  }

  private List<RecordDraftCommand.ChildRows> childCommands(RecordView formal) {
    return formal.children().stream().map(group -> new RecordDraftCommand.ChildRows(
        group.subTypeId(), group.rows().stream().map(row -> new RecordDraftCommand.ChildRowCommand(
            row.id(), row.rowOrder(), row.values())).toList())).toList();
  }

  private void as(UserPrincipal principal) {
    actor.set(principal);
  }

  private <T> T inTransaction(Supplier<T> action) {
    return transactions.execute(status -> action.get());
  }

  private AuthorizationService authorization() {
    AuthorizationService authorization = mock(AuthorizationService.class);
    when(authorization.requireRole(any(Role[].class))).thenAnswer(invocation -> actor.get());
    when(authorization.requireDepartment(anyLong())).thenAnswer(invocation -> {
      long departmentId = invocation.getArgument(0);
      UserPrincipal current = actor.get();
      if (current.department() == null || current.department().id() != departmentId) {
        throw BusinessException.forbidden();
      }
      return current;
    });
    return authorization;
  }

  private MetadataRepository metadata() {
    MetadataRepository metadata = mock(MetadataRepository.class);
    when(metadata.findAssignedMasterType(1L))
        .thenReturn(new MasterType(MASTER_TYPE_ID, "CUS", "Customer", MetadataStatus.ACTIVE));
    when(metadata.findMasterFields(1L, MASTER_TYPE_ID)).thenReturn(List.of(
        field(1, MASTER_TYPE_ID, "name", true),
        field(2, MASTER_TYPE_ID, "internalNote", false)));
    when(metadata.findSubTypes(1L, MASTER_TYPE_ID)).thenReturn(List.of(
        new SubType(CONTACT_TYPE_ID, MASTER_TYPE_ID, "CONTACT", "Contact", MetadataStatus.ACTIVE),
        new SubType(ADDRESS_TYPE_ID, MASTER_TYPE_ID, "ADDRESS", "Address", MetadataStatus.ACTIVE)));
    when(metadata.findSubFields(1L, CONTACT_TYPE_ID)).thenReturn(List.of(
        field(3, CONTACT_TYPE_ID, "phone", true),
        field(4, CONTACT_TYPE_ID, "privateNote", false)));
    when(metadata.findSubFields(1L, ADDRESS_TYPE_ID)).thenReturn(List.of(
        field(5, ADDRESS_TYPE_ID, "city", true),
        field(6, ADDRESS_TYPE_ID, "doorCode", false)));
    return metadata;
  }

  private FieldDefinition field(long id, long ownerId, String code, boolean shared) {
    return new FieldDefinition(id, ownerId, code, code, FieldType.TEXT, true, List.of(), shared,
        (int) id, MetadataStatus.ACTIVE);
  }

  private UserPrincipal principal(long id, String username, String displayName, long departmentId,
      String departmentCode, Role role) {
    return new UserPrincipal(id, username, displayName,
        new DepartmentPrincipal(departmentId, departmentCode, departmentCode), List.of(role));
  }

  private void createSchema() throws Exception {
    try (var connection = DriverManager.getConnection(serverUrl, username, password);
        var statement = connection.createStatement()) {
      assertThat(statement.executeQuery("SELECT VERSION()").next()).isTrue();
      statement.execute("CREATE DATABASE `" + schema + "`");
    }
    Flyway.configure().dataSource(schemaUrl(), username, password)
        .locations("classpath:db/migration").load().migrate();
  }

  private void seedDatabase() {
    var sql = jdbc.getJdbcTemplate();
    sql.update("INSERT INTO departments(id,code,name,status) VALUES(1,'D1','Department 1','ACTIVE'),(2,'D2','Department 2','ACTIVE')");
    sql.update("INSERT INTO users(id,username,password_hash,display_name,department_id,status) VALUES(1,'editor','x','Editor One',1,'ACTIVE'),(2,'approver','x','Approver',1,'ACTIVE'),(3,'editor2','x','Editor Two',1,'ACTIVE'),(4,'viewer','x','Viewer',1,'ACTIVE'),(5,'foreign','x','Foreign Viewer',2,'ACTIVE')");
    sql.update("INSERT INTO master_types(id,code,name,status,created_by) VALUES(1,'CUS','Customer','ACTIVE',1)");
    sql.update("INSERT INTO department_master_types(department_id,master_type_id,status) VALUES(1,1,'ACTIVE'),(2,1,'ACTIVE')");
    sql.update("INSERT INTO sub_types(id,master_type_id,department_id,code,name,sort_order,status) VALUES(1,1,1,'CONTACT','Contact',0,'ACTIVE'),(2,1,1,'ADDRESS','Address',1,'ACTIVE')");
    sql.update("INSERT INTO master_type_code_rules(master_type_id,pattern,sequence_width) VALUES(1,'CUS-{YYYYMMDD}-{SEQ:4}',4)");
  }

  private void startRedis() throws IOException {
    int port = availablePort();
    redisExecutable = redisExecutable();
    redisServer = new RedisServer(redisExecutable.toFile(), port);
    redisServer.start();
    redisConnections = new LettuceConnectionFactory("127.0.0.1", port);
    redisConnections.afterPropertiesSet();
    redis = new StringRedisTemplate(redisConnections);
    redis.afterPropertiesSet();
  }

  private int availablePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private Path redisExecutable() throws IOException {
    try (InputStream source = RedisServer.class.getResourceAsStream("/redis-server-2.8.19.exe")) {
      if (source == null) throw new IllegalStateException("Embedded Redis executable is unavailable");
      Path result = Files.createTempFile("mdm-task9-redis-", ".exe");
      Files.copy(source, result, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      File file = result.toFile();
      if (!file.setExecutable(true)) throw new IllegalStateException("Embedded Redis is not runnable");
      return result;
    }
  }

  private String schemaUrl() {
    int query = serverUrl.indexOf('?');
    String parameters = query < 0 ? "" : serverUrl.substring(query);
    String base = query < 0 ? serverUrl : serverUrl.substring(0, query);
    int pathStart = base.indexOf('/', base.indexOf("://") + 3);
    return (pathStart < 0 ? base : base.substring(0, pathStart)) + "/" + schema + parameters;
  }

  @Override public void close() throws Exception {
    RuntimeException cleanupFailure = null;
    try {
      if (redis != null) redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    } catch (RuntimeException failure) {
      cleanupFailure = failure;
    }
    if (redisConnections != null) redisConnections.destroy();
    if (redisServer != null) redisServer.stop();
    if (redisExecutable != null) Files.deleteIfExists(redisExecutable);
    try (var connection = DriverManager.getConnection(serverUrl, username, password);
        var statement = connection.createStatement()) {
      if (schema.matches("mdm_task9_[0-9a-f]{32}")) {
        statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      }
    } finally {
      if (container != null) container.stop();
    }
    if (cleanupFailure != null) throw cleanupFailure;
  }
}
