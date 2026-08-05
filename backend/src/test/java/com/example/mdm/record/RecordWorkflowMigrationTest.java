package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

class RecordWorkflowMigrationTest {
  @Test void migrationAppliesAndCreatesWorkflowSchemaWithCorrectBackfill() throws Exception {
    try (MigrationDatabase database = MigrationDatabase.start()) {
      database.createSchema();
      Flyway.configure().dataSource(database.schemaUrl(), database.username, database.password)
          .locations("classpath:db/migration").target("5").load().migrate();
      database.insertPreV6Drafts();
      Flyway.configure().dataSource(database.schemaUrl(), database.username, database.password)
          .locations("classpath:db/migration").load().migrate();

      assertThat(database.columnExists("master_record_drafts", "record_action")).isTrue();
      assertThat(database.columnExists("master_record_drafts", "base_version")).isTrue();
      assertThat(database.columnExists("master_record_drafts", "delete_reason")).isTrue();
      assertThat(database.columnExists("master_record_drafts", "approval_task_id")).isTrue();
      assertThat(database.columnExists("sub_record_drafts", "row_order")).isTrue();
      assertThat(database.columnExists("sub_records", "row_order")).isTrue();
      assertThat(database.indexColumns("master_record_drafts", "uk_master_record_drafts_active"))
          .containsExactly("department_id", "master_type_id", "active_record_code");
      assertThat(database.uniqueIndex("master_record_drafts", "uk_master_record_drafts_active")).isTrue();
      assertThat(database.indexColumns("master_records", "uk_master_records_department_type_code"))
          .containsExactly("department_id", "master_type_id", "record_code");
      assertThat(database.uniqueIndex("master_records", "uk_master_records_department_type_code")).isTrue();
      assertThat(database.indexColumns("master_records", "idx_master_records_master_type"))
          .containsExactly("master_type_id");
      assertThat(database.indexColumns("code_sequences", "PRIMARY"))
          .containsExactly("master_type_id", "sequence_date");
      assertThat(database.columnType("code_sequences", "sequence_date")).isEqualTo("date");
      assertThat(database.foreignKeyExists("master_record_drafts", "approval_task_id", "approval_tasks"))
          .isTrue();
      assertThat(database.foreignKeyExists("master_records", "master_type_id", "master_types"))
          .isTrue();
      assertThat(database.queryString("SELECT record_action FROM master_record_drafts WHERE record_code='UPD'"))
          .isEqualTo("UPDATE");
      assertThat(database.queryString("SELECT record_action FROM master_record_drafts WHERE record_code='NEW'"))
          .isEqualTo("CREATE");
    }
  }

  private static final class MigrationDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final MySQLContainer<?> container;
    private final String schema = "mdm_task1_" + UUID.randomUUID().toString().replace("-", "");

    private MigrationDatabase(String serverUrl, String username, String password, MySQLContainer<?> container) {
      this.serverUrl = serverUrl;
      this.username = username;
      this.password = password;
      this.container = container;
    }

    static MigrationDatabase start() {
      String localUrl = System.getProperty("record.migration.mysql.server-url");
      if (localUrl != null) {
        return new MigrationDatabase(localUrl,
            System.getProperty("record.migration.mysql.username", "root"),
            System.getProperty("record.migration.mysql.password", ""), null);
      }
      var container = new MySQLContainer<>("mysql:8.0.36");
      container.start();
      return new MigrationDatabase(container.getJdbcUrl(), "root", container.getPassword(), container);
    }

    void createSchema() throws Exception {
      try (var connection = DriverManager.getConnection(serverUrl, username, password);
          var statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE `" + schema + "`");
      }
    }

    String schemaUrl() {
      int query = serverUrl.indexOf('?');
      String parameters = query < 0 ? "" : serverUrl.substring(query);
      String base = query < 0 ? serverUrl : serverUrl.substring(0, query);
      int authorityStart = base.indexOf("://") + 3;
      int pathStart = base.indexOf('/', authorityStart);
      return (pathStart < 0 ? base : base.substring(0, pathStart)) + "/" + schema + parameters;
    }

    void insertPreV6Drafts() throws Exception {
      try (var connection = DriverManager.getConnection(schemaUrl(), username, password);
          var statement = connection.createStatement()) {
        statement.executeUpdate("INSERT INTO departments(code,name,status) VALUES('TASK1','Task 1','ACTIVE')");
        statement.executeUpdate("INSERT INTO users(username,password_hash,display_name,status) "
            + "VALUES('task1','x','Task 1','ACTIVE')");
        statement.executeUpdate("INSERT INTO master_types(code,name,status,created_by) "
            + "VALUES('TASK1','Task 1','ACTIVE',1)");
        statement.executeUpdate("INSERT INTO master_records(master_type_id,department_id,record_code,field_values,status) "
            + "VALUES(1,1,'UPD','{}','ACTIVE')");
        statement.executeUpdate("INSERT INTO master_record_drafts(master_record_id,master_type_id,department_id,"
            + "record_code,field_values,status,created_by) VALUES(1,1,1,'UPD','{}','DRAFT',1)");
        statement.executeUpdate("INSERT INTO master_record_drafts(master_type_id,department_id,record_code,"
            + "field_values,status,created_by) VALUES(1,1,'NEW','{}','DRAFT',1)");
      }
    }

    boolean columnExists(String table, String column) throws Exception {
      return queryBoolean("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='" + schema
          + "' AND table_name='" + table + "' AND column_name='" + column + "'");
    }

    String columnType(String table, String column) throws Exception {
      return queryString("SELECT data_type FROM information_schema.columns WHERE table_schema='" + schema
          + "' AND table_name='" + table + "' AND column_name='" + column + "'");
    }

    java.util.List<String> indexColumns(String table, String index) throws Exception {
      try (var connection = DriverManager.getConnection(schemaUrl(), username, password);
          var statement = connection.createStatement();
          var result = statement.executeQuery("SELECT column_name FROM information_schema.statistics WHERE table_schema='"
              + schema + "' AND table_name='" + table + "' AND index_name='" + index + "' ORDER BY seq_in_index")) {
        var columns = new java.util.ArrayList<String>();
        while (result.next()) columns.add(result.getString(1));
        return columns;
      }
    }

    boolean uniqueIndex(String table, String index) throws Exception {
      return queryBoolean("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='" + schema
          + "' AND table_name='" + table + "' AND index_name='" + index + "' AND non_unique=0");
    }

    boolean foreignKeyExists(String table, String column, String referencedTable) throws Exception {
      return queryBoolean("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema='" + schema
          + "' AND table_name='" + table + "' AND column_name='" + column + "' AND referenced_table_name='"
          + referencedTable + "'");
    }

    boolean queryBoolean(String sql) throws Exception { return Integer.parseInt(queryString(sql)) > 0; }

    String queryString(String sql) throws Exception {
      try (var connection = DriverManager.getConnection(schemaUrl(), username, password);
          Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
        assertThat(result.next()).isTrue();
        return result.getString(1);
      }
    }

    @Override public void close() throws Exception {
      try (var connection = DriverManager.getConnection(serverUrl, username, password);
          var statement = connection.createStatement()) {
        if (schema.matches("mdm_task1_[0-9a-f]{32}")) statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      } finally {
        if (container != null) container.stop();
      }
    }
  }
}
