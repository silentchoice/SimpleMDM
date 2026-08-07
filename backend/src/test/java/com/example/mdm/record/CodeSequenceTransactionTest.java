package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mdm.auth.AuthorizationService;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

class CodeSequenceTransactionTest {
  private static final LocalDate SEQUENCE_DATE = LocalDate.of(2026, 8, 5);

  @Test void allocationCommitsEvenWhenTheCallingDraftTransactionRollsBack() throws Exception {
    try (SequenceDatabase database = SequenceDatabase.start()) {
      var service = database.service();

      assertThatThrownBy(() -> database.transactions.executeWithoutResult(status -> {
        assertThat(service.allocate(1L, SEQUENCE_DATE)).isEqualTo("CUS-20260805-0001");
        throw new IllegalStateException("roll back the draft");
      })).isInstanceOf(IllegalStateException.class);

      assertThat(service.allocate(1L, SEQUENCE_DATE)).isEqualTo("CUS-20260805-0002");
    }
  }

  @Test void allocationAdvancesPastCodesThatAlreadyExist() throws Exception {
    try (SequenceDatabase database = SequenceDatabase.start()) {
      database.insertFormalRecord("CUS-20260805-0001");

      assertThat(database.service().allocate(1L, SEQUENCE_DATE))
          .isEqualTo("CUS-20260805-0002");
      assertThat(database.nextValue()).isEqualTo(3L);
    }
  }

  @Test void concurrentAllocationsAreUniqueAndAdvanceTheSequenceExactlyOnceEach()
      throws Exception {
    try (SequenceDatabase database = SequenceDatabase.start()) {
      int allocationCount = 12;
      var start = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(allocationCount);
      try {
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        for (int index = 0; index < allocationCount; index++) {
          futures.add(executor.submit(() -> {
            start.await();
            return database.service().allocate(1L, SEQUENCE_DATE);
          }));
        }
        start.countDown();
        var allocated = new ArrayList<String>();
        for (var future : futures) allocated.add(future.get());

        assertThat(new HashSet<>(allocated)).hasSize(allocationCount);
        assertThat(allocated).containsExactlyInAnyOrderElementsOf(expectedCodes(allocationCount));
        assertThat(database.nextValue()).isEqualTo(allocationCount + 1L);
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private List<String> expectedCodes(int count) {
    var result = new ArrayList<String>();
    for (int value = 1; value <= count; value++) {
      result.add("CUS-20260805-" + String.format("%04d", value));
    }
    return result;
  }

  private static final class SequenceDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final MySQLContainer<?> container;
    private final String schema = "mdm_task2_" + UUID.randomUUID().toString().replace("-", "");
    private DriverManagerDataSource dataSource;
    private NamedParameterJdbcTemplate jdbc;
    private TransactionTemplate transactions;

    private SequenceDatabase(String serverUrl, String username, String password,
        MySQLContainer<?> container) {
      this.serverUrl = serverUrl;
      this.username = username;
      this.password = password;
      this.container = container;
    }

    static SequenceDatabase start() throws Exception {
      WorkflowTestEnvironment.MySqlSettings local = WorkflowTestEnvironment.mysql();
      var database = local == null
          ? containerDatabase()
          : new SequenceDatabase(local.serverUrl(), local.username(), local.password(), null);
      try {
        database.initialize();
        return database;
      } catch (Exception failure) {
        throw WorkflowTestEnvironment.withCleanupFailure(failure, database::close);
      }
    }

    private static SequenceDatabase containerDatabase() {
      var container = new MySQLContainer<>("mysql:8.0.36");
      container.start();
      return new SequenceDatabase(container.getJdbcUrl(), "root", container.getPassword(), container);
    }

    private void initialize() throws Exception {
      try (var connection = DriverManager.getConnection(serverUrl, username, password);
          var statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE `" + schema + "`");
      }
      Flyway.configure().dataSource(schemaUrl(), username, password)
          .locations("classpath:db/migration").load().migrate();
      dataSource = new DriverManagerDataSource(schemaUrl(), username, password);
      jdbc = new NamedParameterJdbcTemplate(dataSource);
      transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
      var sql = jdbc.getJdbcTemplate();
      sql.update("INSERT INTO departments(id,code,name,status) VALUES(1,'D1','Department 1','ACTIVE')");
      sql.update("INSERT INTO users(id,username,password_hash,display_name,department_id,status) "
          + "VALUES(1,'editor','x','Editor',1,'ACTIVE')");
      sql.update("INSERT INTO master_types(id,code,name,status,created_by) "
          + "VALUES(1,'CUS','Customer','ACTIVE',1)");
      sql.update("INSERT INTO master_type_code_rules(master_type_id,pattern,sequence_width) "
          + "VALUES(1,'CUS-{yyyyMMdd}-{0001}',4)");
    }

    CodeRuleService service() {
      return new CodeRuleService(new JdbcCodeSequenceRepository(jdbc), new CodeRuleParser(),
          org.mockito.Mockito.mock(AuthorizationService.class), Clock.systemUTC());
    }

    void insertFormalRecord(String code) {
      jdbc.getJdbcTemplate().update("INSERT INTO master_records(master_type_id,department_id,"
          + "record_code,field_values,status,created_by) VALUES(1,1,?,'{}','ACTIVE',1)", code);
    }

    long nextValue() {
      return jdbc.getJdbcTemplate().queryForObject("SELECT next_value FROM code_sequences "
          + "WHERE master_type_id=1 AND sequence_date=?", Long.class, SEQUENCE_DATE);
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
