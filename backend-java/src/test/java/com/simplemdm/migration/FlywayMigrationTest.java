package com.simplemdm.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {

    @Test
    void migratesEmptyDatabaseWithRequiredTablesAndSystemBoundaries() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(tableNames(connection)).contains(
                "SYS_SYSTEM", "SYS_DEPARTMENT", "SYS_USER", "SYS_ROLE", "SYS_PERMISSION",
                "SYS_USER_ROLE", "SYS_ROLE_PERMISSION", "SYS_USER_DEPARTMENT_SCOPE",
                "MDM_OBJECT_TYPE", "MDM_FIELD_DEFINITION", "MDM_CHILD_TYPE", "MDM_CHILD_FIELD_DEFINITION",
                "MDM_RECORD", "MDM_RECORD_VALUE", "MDM_CHILD_RECORD", "MDM_CHILD_RECORD_VALUE",
                "WF_APPROVAL_REQUEST", "WF_APPROVAL_CHANGE", "WF_APPROVAL_ACTION", "SYS_APPROVER_ASSIGNMENT",
                "SYS_PUSH_ENDPOINT", "SYS_PUSH_SUBSCRIPTION", "SYS_PUSH_LOG");

            assertThat(importedKeys(connection, "SYS_USER")).contains(
                foreignKey("FK_USER_DEPARTMENT_SYSTEM", "SYS_DEPARTMENT", Map.of("SYSTEM_ID", "SYSTEM_ID", "DEPARTMENT_ID", "ID")),
                foreignKey("FK_USER_CREATED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CREATED_BY", "ID")),
                foreignKey("FK_USER_UPDATED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "UPDATED_BY", "ID")));
            assertThat(importedKeys(connection, "SYS_USER_ROLE")).contains(
                foreignKey("FK_USER_ROLE_USER_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "USER_ID", "ID")),
                foreignKey("FK_USER_ROLE_ROLE_SYSTEM", "SYS_ROLE", Map.of("SYSTEM_ID", "SYSTEM_ID", "ROLE_ID", "ID")));
            assertThat(importedKeys(connection, "SYS_USER_DEPARTMENT_SCOPE")).contains(
                foreignKey("FK_SCOPE_USER_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "USER_ID", "ID")),
                foreignKey("FK_SCOPE_DEPARTMENT_SYSTEM", "SYS_DEPARTMENT", Map.of("SYSTEM_ID", "SYSTEM_ID", "DEPARTMENT_ID", "ID")));
            assertThat(importedKeys(connection, "MDM_OBJECT_TYPE")).contains(
                foreignKey("FK_OBJECT_TYPE_CREATED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CREATED_BY", "ID")),
                foreignKey("FK_OBJECT_TYPE_UPDATED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "UPDATED_BY", "ID")));            assertThat(importedKeys(connection, "MDM_FIELD_DEFINITION")).contains(
                foreignKey("FK_FIELD_OBJECT_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "OBJECT_TYPE_ID", "ID")),
                foreignKey("FK_FIELD_REFERENCE_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "REFERENCE_OBJECT_TYPE_ID", "ID")),
                foreignKey("FK_FIELD_CREATED_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CREATED_BY", "ID")));
            assertThat(importedKeys(connection, "MDM_CHILD_TYPE")).contains(
                foreignKey("FK_CHILD_TYPE_OBJECT_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "OBJECT_TYPE_ID", "ID")));
            assertThat(importedKeys(connection, "MDM_CHILD_FIELD_DEFINITION")).contains(
                foreignKey("FK_CHILD_FIELD_TYPE_SYSTEM", "MDM_CHILD_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "CHILD_TYPE_ID", "ID")),
                foreignKey("FK_CHILD_FIELD_REFERENCE_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "REFERENCE_OBJECT_TYPE_ID", "ID")));
            assertThat(importedKeys(connection, "MDM_RECORD_VALUE")).contains(
                foreignKey("FK_RECORD_VALUE_RECORD_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "RECORD_ID", "ID")),
                foreignKey("FK_RECORD_VALUE_FIELD_SYSTEM", "MDM_FIELD_DEFINITION", Map.of("SYSTEM_ID", "SYSTEM_ID", "FIELD_DEFINITION_ID", "ID")),
                foreignKey("FK_RECORD_VALUE_REFERENCE_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "REFERENCE_RECORD_ID", "ID")));
            assertThat(importedKeys(connection, "MDM_CHILD_RECORD")).contains(
                foreignKey("FK_CHILD_RECORD_RECORD_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "RECORD_ID", "ID")),
                foreignKey("FK_CHILD_RECORD_TYPE_SYSTEM", "MDM_CHILD_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "CHILD_TYPE_ID", "ID")));
            assertThat(importedKeys(connection, "MDM_CHILD_RECORD")).contains(
                foreignKey("FK_CHILD_RECORD_CREATED_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CREATED_BY", "ID")),
                foreignKey("FK_CHILD_RECORD_UPDATED_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "UPDATED_BY", "ID")));            assertThat(importedKeys(connection, "MDM_CHILD_RECORD_VALUE")).contains(
                foreignKey("FK_CHILD_VALUE_RECORD_SYSTEM", "MDM_CHILD_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "CHILD_RECORD_ID", "ID")),
                foreignKey("FK_CHILD_VALUE_FIELD_SYSTEM", "MDM_CHILD_FIELD_DEFINITION", Map.of("SYSTEM_ID", "SYSTEM_ID", "FIELD_DEFINITION_ID", "ID")),
                foreignKey("FK_CHILD_VALUE_REFERENCE_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "REFERENCE_RECORD_ID", "ID")),
                foreignKey("FK_CHILD_VALUE_CREATED_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CREATED_BY", "ID")),
                foreignKey("FK_CHILD_VALUE_UPDATED_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "UPDATED_BY", "ID")));
            assertThat(importedKeys(connection, "WF_APPROVAL_CHANGE")).contains(
                foreignKey("FK_APPROVAL_CHANGE_REQUEST_SYSTEM", "WF_APPROVAL_REQUEST", Map.of("SYSTEM_ID", "SYSTEM_ID", "APPROVAL_REQUEST_ID", "ID")),
                foreignKey("FK_APPROVAL_CHANGE_FIELD_SYSTEM", "MDM_FIELD_DEFINITION", Map.of("SYSTEM_ID", "SYSTEM_ID", "FIELD_DEFINITION_ID", "ID")));            assertThat(importedKeys(connection, "MDM_RECORD")).contains(
                foreignKey("FK_RECORD_OBJECT_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "OBJECT_TYPE_ID", "ID")),
                foreignKey("FK_RECORD_DEPARTMENT_SYSTEM", "SYS_DEPARTMENT", Map.of("SYSTEM_ID", "SYSTEM_ID", "DEPARTMENT_ID", "ID")),
                foreignKey("FK_RECORD_CREATED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CREATED_BY", "ID")),
                foreignKey("FK_RECORD_UPDATED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "UPDATED_BY", "ID")));
            assertThat(importedKeys(connection, "WF_APPROVAL_REQUEST")).contains(
                foreignKey("FK_APPROVAL_OBJECT_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "OBJECT_TYPE_ID", "ID")),
                foreignKey("FK_APPROVAL_RECORD_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "RECORD_ID", "ID")),
                foreignKey("FK_APPROVAL_DEPARTMENT_SYSTEM", "SYS_DEPARTMENT", Map.of("SYSTEM_ID", "SYSTEM_ID", "DEPARTMENT_ID", "ID")),
                foreignKey("FK_APPROVAL_USER_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "REQUESTED_BY", "ID")));
            assertThat(importedKeys(connection, "WF_APPROVAL_ACTION")).contains(
                foreignKey("FK_APPROVAL_ACTION_REQUEST_SYSTEM", "WF_APPROVAL_REQUEST", Map.of("SYSTEM_ID", "SYSTEM_ID", "APPROVAL_REQUEST_ID", "ID")),
                foreignKey("FK_APPROVAL_ACTION_ACTOR_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "ACTOR_ID", "ID")));
            assertThat(importedKeys(connection, "SYS_APPROVER_ASSIGNMENT")).contains(
                foreignKey("FK_APPROVER_OBJECT_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "OBJECT_TYPE_ID", "ID")),
                foreignKey("FK_APPROVER_DEPARTMENT_SYSTEM", "SYS_DEPARTMENT", Map.of("SYSTEM_ID", "SYSTEM_ID", "DEPARTMENT_ID", "ID")),
                foreignKey("FK_APPROVER_USER_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "APPROVER_USER_ID", "ID")));
            assertThat(importedKeys(connection, "SYS_PUSH_SUBSCRIPTION")).contains(
                foreignKey("FK_SUBSCRIPTION_ENDPOINT_SYSTEM", "SYS_PUSH_ENDPOINT", Map.of("SYSTEM_ID", "SYSTEM_ID", "ENDPOINT_ID", "ID")),
                foreignKey("FK_SUBSCRIPTION_OBJECT_SYSTEM", "MDM_OBJECT_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "OBJECT_TYPE_ID", "ID")));
            assertThat(importedKeys(connection, "SYS_PUSH_LOG")).contains(
                foreignKey("FK_PUSH_LOG_SUBSCRIPTION_SYSTEM", "SYS_PUSH_SUBSCRIPTION", Map.of("SYSTEM_ID", "SYSTEM_ID", "SUBSCRIPTION_ID", "ID")),
                foreignKey("FK_PUSH_LOG_RECORD_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "RECORD_ID", "ID")));

            assertThat(uniqueIndexes(connection, "WF_APPROVAL_CHANGE"))
                .contains(List.of("APPROVAL_REQUEST_ID", "FIELD_DEFINITION_ID"));
            assertThat(uniqueIndexes(connection, "MDM_FIELD_DEFINITION"))
                .contains(List.of("OBJECT_TYPE_ID", "FIELD_KEY"));
            assertThat(checkConstraintNames(connection, "WF_APPROVAL_CHANGE"))
                .contains("CK_APPROVAL_CHANGE_OLD_ONE_TYPE", "CK_APPROVAL_CHANGE_NEW_ONE_TYPE");
        }
    }

    private Set<String> tableNames(Connection connection) throws SQLException {
        Set<String> tableNames = new HashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
        }
        return tableNames;
    }

    private Set<ImportedKey> importedKeys(Connection connection, String tableName) throws SQLException {
        Map<String, ImportedKeyBuilder> keysByName = new HashMap<>();
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (keys.next()) {
                String keyName = keys.getString("FK_NAME");
                ImportedKeyBuilder key = keysByName.get(keyName);
                if (key == null) {
                    key = new ImportedKeyBuilder(keyName, keys.getString("PKTABLE_NAME"));
                    keysByName.put(keyName, key);
                }
                key.columns.put(keys.getString("FKCOLUMN_NAME"), keys.getString("PKCOLUMN_NAME"));
            }
        }
        Set<ImportedKey> importedKeys = new HashSet<>();
        for (ImportedKeyBuilder key : keysByName.values()) {
            importedKeys.add(new ImportedKey(key.name, key.primaryTable, Map.copyOf(key.columns)));
        }
        return importedKeys;
    }

    private Set<List<String>> uniqueIndexes(Connection connection, String tableName) throws SQLException {
        Map<String, TreeMap<Short, String>> columnsByIndex = new HashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, true, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                String columnName = indexes.getString("COLUMN_NAME");
                if (indexName != null && columnName != null) {
                    columnsByIndex.computeIfAbsent(indexName, ignored -> new TreeMap<>())
                        .put(indexes.getShort("ORDINAL_POSITION"), columnName);
                }
            }
        }
        Set<List<String>> uniqueIndexes = new HashSet<>();
        for (TreeMap<Short, String> columns : columnsByIndex.values()) {
            uniqueIndexes.add(new ArrayList<>(columns.values()));
        }
        return uniqueIndexes;
    }

    private Set<String> checkConstraintNames(Connection connection, String tableName) throws SQLException {
        Set<String> constraintNames = new HashSet<>();
        try (var statement = connection.prepareStatement("""
            SELECT CONSTRAINT_NAME
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE TABLE_NAME = ? AND CONSTRAINT_TYPE = 'CHECK'
            """)) {
            statement.setString(1, tableName);
            try (ResultSet constraints = statement.executeQuery()) {
                while (constraints.next()) {
                    constraintNames.add(constraints.getString("CONSTRAINT_NAME"));
                }
            }
        }
        return constraintNames;
    }

    private ImportedKey foreignKey(String name, String primaryTable, Map<String, String> columns) {
        return new ImportedKey(name, primaryTable, Map.copyOf(columns));
    }

    private record ImportedKey(String name, String primaryTable, Map<String, String> columns) {
    }

    private static final class ImportedKeyBuilder {
        private final String name;
        private final String primaryTable;
        private final Map<String, String> columns = new TreeMap<>();

        private ImportedKeyBuilder(String name, String primaryTable) {
            this.name = name;
            this.primaryTable = primaryTable;
        }
    }
}