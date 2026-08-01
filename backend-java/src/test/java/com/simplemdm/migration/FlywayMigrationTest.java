package com.simplemdm.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayMigrationTest {

    @Test
    void migratesEmptyDatabaseWithRequiredTablesAndSystemBoundaries() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(tableNames(connection)).contains(
                "SYS_SYSTEM", "SYS_DEPARTMENT", "SYS_USER", "SYS_ROLE", "SYS_PERMISSION",
                "SYS_USER_ROLE", "SYS_ROLE_PERMISSION", "SYS_USER_DEPARTMENT_SCOPE",
                "MDM_OBJECT_TYPE", "MDM_FIELD_DEFINITION", "MDM_CHILD_TYPE", "MDM_CHILD_FIELD_DEFINITION",
                "MDM_RECORD", "MDM_RECORD_VALUE", "MDM_CHILD_RECORD", "MDM_CHILD_RECORD_VALUE",
                "WF_APPROVAL_REQUEST", "WF_APPROVAL_CHANGE", "WF_APPROVAL_CHILD_CHANGE",
                "WF_APPROVAL_CHILD_VALUE_CHANGE", "WF_APPROVAL_ACTION", "SYS_APPROVER_ASSIGNMENT",
                "SYS_PUSH_ENDPOINT", "SYS_PUSH_SUBSCRIPTION", "SYS_PUSH_LOG", "MDM_METADATA_AUDIT");

            assertThat(columns(connection, "MDM_CHILD_FIELD_DEFINITION")).contains(
                column("SHARED", Types.BOOLEAN, false));
            assertThat(columns(connection, "WF_APPROVAL_REQUEST")).contains(
                column("RECORD_ID", Types.BIGINT, true),
                column("EXPECTED_VERSION", Types.BIGINT, true),
                column("OPERATION", Types.CHAR, false),
                column("RECORD_CODE", Types.CHAR, true));
            assertThat(columns(connection, "WF_APPROVAL_CHILD_CHANGE")).contains(
                column("ID", Types.BIGINT, false),
                column("SYSTEM_ID", Types.BIGINT, false),
                column("APPROVAL_REQUEST_ID", Types.BIGINT, false),
                column("CHANGE_KEY", Types.CHAR, false),
                column("CHILD_TYPE_ID", Types.BIGINT, false),
                column("CHILD_RECORD_ID", Types.BIGINT, true),
                column("OPERATION", Types.CHAR, false),
                column("EXPECTED_VERSION", Types.BIGINT, true),
                column("SORT_ORDER", Types.INTEGER, false),
                column("CREATED_AT", Types.TIMESTAMP, false));
            assertThat(columns(connection, "WF_APPROVAL_CHILD_VALUE_CHANGE")).contains(
                column("ID", Types.BIGINT, false),
                column("SYSTEM_ID", Types.BIGINT, false),
                column("APPROVAL_CHILD_CHANGE_ID", Types.BIGINT, false),
                column("FIELD_DEFINITION_ID", Types.BIGINT, false),
                column("OLD_STRING_VALUE", Types.CHAR, true),
                column("OLD_TEXT_VALUE", Types.CHAR, true),
                column("OLD_INTEGER_VALUE", Types.BIGINT, true),
                column("OLD_DECIMAL_VALUE", Types.DECIMAL, true),
                column("OLD_BOOLEAN_VALUE", Types.BOOLEAN, true),
                column("OLD_DATE_VALUE", Types.DATE, true),
                column("OLD_DATETIME_VALUE", Types.TIMESTAMP, true),
                column("OLD_REFERENCE_RECORD_ID", Types.BIGINT, true),
                column("NEW_STRING_VALUE", Types.CHAR, true),
                column("NEW_TEXT_VALUE", Types.CHAR, true),
                column("NEW_INTEGER_VALUE", Types.BIGINT, true),
                column("NEW_DECIMAL_VALUE", Types.DECIMAL, true),
                column("NEW_BOOLEAN_VALUE", Types.BOOLEAN, true),
                column("NEW_DATE_VALUE", Types.DATE, true),
                column("NEW_DATETIME_VALUE", Types.TIMESTAMP, true),
                column("NEW_REFERENCE_RECORD_ID", Types.BIGINT, true),
                column("CREATED_AT", Types.TIMESTAMP, false));
            assertThat(columns(connection, "SYS_PUSH_LOG")).contains(
                column("TRIGGER_TYPE", Types.CHAR, false),
                column("TRIGGERED_BY", Types.BIGINT, true),
                column("TRIGGER_REASON", Types.CHAR, true),
                column("LAST_RETRY_BY", Types.BIGINT, true),
                column("LAST_RETRY_REASON", Types.CHAR, true),
                column("LAST_RETRY_AT", Types.TIMESTAMP, true),
                column("IDEMPOTENCY_KEY", Types.CHAR, false),
                column("ACTIVE_DEDUP_KEY", Types.CHAR, true),
                column("CANCELLED_BY", Types.BIGINT, true),
                column("CANCELLED_AT", Types.TIMESTAMP, true),
                column("CANCELLATION_REASON", Types.CHAR, true));
            assertThat(columns(connection, "SYS_PUSH_ENDPOINT")).contains(
                column("SCHEDULE_ENABLED", Types.BOOLEAN, false),
                column("SCHEDULE_CRON", Types.CHAR, true),
                column("SCHEDULE_TIMEZONE", Types.CHAR, true),
                column("SCHEDULE_NEXT_AT", Types.TIMESTAMP, true),
                column("SCHEDULE_LAST_AT", Types.TIMESTAMP, true));

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
                foreignKey("FK_PUSH_LOG_RECORD_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "RECORD_ID", "ID")),
                foreignKey("FK_PUSH_LOG_TRIGGERED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "TRIGGERED_BY", "ID")),
                foreignKey("FK_PUSH_LOG_LAST_RETRY_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "LAST_RETRY_BY", "ID")),
                foreignKey("FK_PUSH_LOG_CANCELLED_BY_SYSTEM", "SYS_USER", Map.of("SYSTEM_ID", "SYSTEM_ID", "CANCELLED_BY", "ID")));
            assertThat(importedKeys(connection, "WF_APPROVAL_CHILD_CHANGE")).contains(
                foreignKey("FK_APPROVAL_CHILD_REQUEST_SYSTEM", "WF_APPROVAL_REQUEST", Map.of("SYSTEM_ID", "SYSTEM_ID", "APPROVAL_REQUEST_ID", "ID")),
                foreignKey("FK_APPROVAL_CHILD_TYPE_SYSTEM", "MDM_CHILD_TYPE", Map.of("SYSTEM_ID", "SYSTEM_ID", "CHILD_TYPE_ID", "ID")),
                foreignKey("FK_APPROVAL_CHILD_RECORD_SYSTEM", "MDM_CHILD_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "CHILD_RECORD_ID", "ID")));
            assertThat(importedKeys(connection, "WF_APPROVAL_CHILD_VALUE_CHANGE")).contains(
                foreignKey("FK_APPROVAL_CHILD_VALUE_CHANGE_SYSTEM", "WF_APPROVAL_CHILD_CHANGE", Map.of("SYSTEM_ID", "SYSTEM_ID", "APPROVAL_CHILD_CHANGE_ID", "ID")),
                foreignKey("FK_APPROVAL_CHILD_VALUE_FIELD_SYSTEM", "MDM_CHILD_FIELD_DEFINITION", Map.of("SYSTEM_ID", "SYSTEM_ID", "FIELD_DEFINITION_ID", "ID")),
                foreignKey("FK_APPROVAL_CHILD_VALUE_OLD_REF_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "OLD_REFERENCE_RECORD_ID", "ID")),
                foreignKey("FK_APPROVAL_CHILD_VALUE_NEW_REF_SYSTEM", "MDM_RECORD", Map.of("SYSTEM_ID", "SYSTEM_ID", "NEW_REFERENCE_RECORD_ID", "ID")));

            assertThat(uniqueIndexes(connection, "WF_APPROVAL_CHANGE"))
                .contains(List.of("APPROVAL_REQUEST_ID", "FIELD_DEFINITION_ID"));
            assertThat(uniqueIndexes(connection, "MDM_FIELD_DEFINITION"))
                .contains(List.of("OBJECT_TYPE_ID", "FIELD_KEY"));
            assertThat(uniqueIndexes(connection, "WF_APPROVAL_CHILD_CHANGE")).contains(
                List.of("APPROVAL_REQUEST_ID", "CHANGE_KEY"),
                List.of("SYSTEM_ID", "ID"));
            assertThat(uniqueIndexes(connection, "WF_APPROVAL_CHILD_VALUE_CHANGE"))
                .contains(List.of("APPROVAL_CHILD_CHANGE_ID", "FIELD_DEFINITION_ID"));
            assertThat(uniqueIndexes(connection, "SYS_PUSH_LOG")).contains(
                List.of("IDEMPOTENCY_KEY"), List.of("ACTIVE_DEDUP_KEY"));
            assertThat(checkConstraintNames(connection, "WF_APPROVAL_CHANGE"))
                .contains("CK_APPROVAL_CHANGE_OLD_ONE_TYPE", "CK_APPROVAL_CHANGE_NEW_ONE_TYPE");
            assertThat(checkConstraintNames(connection, "WF_APPROVAL_CHILD_VALUE_CHANGE"))
                .contains("CK_APPROVAL_CHILD_VALUE_OLD_ONE_TYPE", "CK_APPROVAL_CHILD_VALUE_NEW_ONE_TYPE");
        }
    }

    @Test
    void upgradesAnExistingV1SchemaIncrementally() throws Exception {
        Flyway v1 = Flyway.configure()
            .dataSource("jdbc:h2:mem:migration-from-v1;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .target("1")
            .load();
        assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);
        try (Connection connection = v1.getConfiguration().getDataSource().getConnection()) {
            insertV1Rows(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE sys_push_endpoint SET endpoint_url="
                    + "'https://legacy-user:legacy-secret@legacy.example/hook' WHERE id=1");
            }
        }

        Flyway latest = Flyway.configure()
            .dataSource(v1.getConfiguration().getDataSource())
            .locations("classpath:db/migration")
            .load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(4);

        try (Connection connection = latest.getConfiguration().getDataSource().getConnection()) {
            assertThat(tableNames(connection)).contains("WF_APPROVAL_CHILD_CHANGE", "WF_APPROVAL_CHILD_VALUE_CHANGE");
            assertThat(columns(connection, "MDM_CHILD_FIELD_DEFINITION")).contains(column("SHARED", Types.BOOLEAN, false));
            assertThat(singleValue(connection, "SELECT COUNT(*) FROM mdm_child_field_definition WHERE id = 1")).isEqualTo(1L);
            assertThat(singleValue(connection, "SELECT shared FROM mdm_child_field_definition WHERE id = 1")).isEqualTo(false);
            assertThat(singleValue(connection, "SELECT operation FROM wf_approval_request WHERE id = 1")).isEqualTo("UPDATE");
            assertThat(singleValue(connection, "SELECT trigger_type FROM sys_push_log WHERE id = 1")).isEqualTo("AUTOMATIC");
            assertThat(singleValue(connection, "SELECT record_id FROM wf_approval_request WHERE id = 1")).isEqualTo(1L);
            assertThat(singleValue(connection, "SELECT expected_version FROM wf_approval_request WHERE id = 1")).isEqualTo(0L);
            assertThat(singleValue(connection, "SELECT record_code FROM wf_approval_request WHERE id = 1")).isNull();
            assertThat(singleValue(connection, "SELECT triggered_by FROM sys_push_log WHERE id = 1")).isNull();
            assertThat(singleValue(connection, "SELECT trigger_reason FROM sys_push_log WHERE id = 1")).isNull();
            assertThat(singleValue(connection, "SELECT endpoint_url FROM sys_push_endpoint WHERE id = 1"))
                .isEqualTo("https://legacy.example/hook");
            assertThat(singleValue(connection, "SELECT status FROM sys_push_endpoint WHERE id = 1"))
                .isEqualTo("inactive");
            assertThat(singleValue(connection, "SELECT endpoint_url FROM sys_push_endpoint WHERE id = 1")
                .toString()).doesNotContain("legacy-user", "legacy-secret", "@");

            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("""
                    INSERT INTO wf_approval_request
                        (id, system_id, object_type_id, record_id, operation, record_code, department_id,
                         requested_by, expected_version, status, submitted_at, created_at, updated_at)
                    VALUES (2, 1, 1, NULL, 'CREATE', 'NEW-001', 1, 1, NULL, 'PENDING',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)).isEqualTo(1);
                assertThat(statement.executeUpdate("""
                    INSERT INTO sys_push_log
                        (id, system_id, subscription_id, record_id, event_id, status, trigger_type,
                         triggered_by, trigger_reason, idempotency_key, active_dedup_key, created_at)
                    VALUES (2, 1, 1, 1, 'event-2', 'PENDING', 'MANUAL', 1, 'operator request',
                            'manual:event-2', 'active:event-2', CURRENT_TIMESTAMP)
                    """)).isEqualTo(1);
            }
            assertThat(singleValue(connection, "SELECT record_id FROM wf_approval_request WHERE id = 2")).isNull();
            assertThat(singleValue(connection, "SELECT expected_version FROM wf_approval_request WHERE id = 2")).isNull();
            assertThat(singleValue(connection, "SELECT operation FROM wf_approval_request WHERE id = 2")).isEqualTo("CREATE");
            assertThat(singleValue(connection, "SELECT record_code FROM wf_approval_request WHERE id = 2")).isEqualTo("NEW-001");
            assertThat(singleValue(connection, "SELECT trigger_type FROM sys_push_log WHERE id = 2")).isEqualTo("MANUAL");
            assertThat(singleValue(connection, "SELECT triggered_by FROM sys_push_log WHERE id = 2")).isEqualTo(1L);
            assertThat(singleValue(connection, "SELECT trigger_reason FROM sys_push_log WHERE id = 2")).isEqualTo("operator request");
        }
    }

    @Test
    void upgradesExistingV2RetryLogsWithoutChangingLogicalIdentity() throws Exception {
        Flyway v2 = Flyway.configure()
            .dataSource("jdbc:h2:mem:migration-from-v2;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration").target("2").load();
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(2);
        try (Connection connection = v2.getConfiguration().getDataSource().getConnection()) {
            insertV1Rows(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE sys_push_log SET request_snapshot='{\"v2\":true}' WHERE id=1");
            }
        }

        Flyway latest = Flyway.configure().dataSource(v2.getConfiguration().getDataSource())
            .locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(3);

        try (Connection connection = latest.getConfiguration().getDataSource().getConnection()) {
            assertThat(singleValue(connection, "SELECT event_id FROM sys_push_log WHERE id=1"))
                .isEqualTo("event-1");
            assertThat(singleValue(connection, "SELECT request_snapshot FROM sys_push_log WHERE id=1"))
                .isEqualTo("{\"v2\":true}");
            assertThat(singleValue(connection, "SELECT last_retry_by FROM sys_push_log WHERE id=1")).isNull();
            assertThat(singleValue(connection, "SELECT last_retry_reason FROM sys_push_log WHERE id=1")).isNull();
            assertThat(singleValue(connection, "SELECT last_retry_at FROM sys_push_log WHERE id=1")).isNull();
        }
    }

    @Test
    void pushRequestSnapshotStoresCompleteJsonBeyondMysqlTextLimit() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:push-snapshot-capacity;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            insertV1Rows(connection);
            String completeJson = "{\"content\":\"" + "x".repeat(70_000) + "\"}";
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sys_push_log SET request_snapshot = ? WHERE id = 1")) {
                statement.setString(1, completeJson);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            assertThat(singleValue(connection,
                "SELECT request_snapshot FROM sys_push_log WHERE id = 1")).isEqualTo(completeJson);
        }
    }

    @Test
    void childTypedValueChecksAllowZeroOrOneValuePerSideAndRejectTwo() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:typed-value-checks;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            insertV2ApprovalChildFixture(connection);

            assertChildValueInsertSucceeds(connection, "created_at", "CURRENT_TIMESTAMP");
            assertChildValueInsertSucceeds(connection, "old_string_value, created_at", "'before', CURRENT_TIMESTAMP");
            assertChildValueInsertSucceeds(connection, "new_integer_value, created_at", "42, CURRENT_TIMESTAMP");
            assertChildValueInsertSucceeds(connection,
                "old_boolean_value, new_date_value, created_at",
                "TRUE, DATE '2026-07-31', CURRENT_TIMESTAMP");

            assertThatThrownBy(() -> insertChildValue(connection,
                "old_string_value, old_integer_value, created_at",
                "'before', 7, CURRENT_TIMESTAMP"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("CK_APPROVAL_CHILD_VALUE_OLD_ONE_TYPE");
            assertThatThrownBy(() -> insertChildValue(connection,
                "new_boolean_value, new_datetime_value, created_at",
                "TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("CK_APPROVAL_CHILD_VALUE_NEW_ONE_TYPE");
        }
    }

    @Test
    void childDeleteCommitsAsSoftDeleteAndRetainsEffectiveValuesAndApprovalAudit() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:child-soft-delete;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            insertV2ApprovalChildFixture(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE wf_approval_child_change SET operation = 'DELETE' WHERE id = 1");
                statement.executeUpdate("""
                    INSERT INTO mdm_child_record_value
                        (id, system_id, child_record_id, field_definition_id, string_value,
                         created_at, updated_at)
                    VALUES (1, 1, 1, 1, 'before-delete', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
                statement.executeUpdate("""
                    INSERT INTO wf_approval_child_value_change
                        (id, system_id, approval_child_change_id, field_definition_id,
                         old_string_value, created_at)
                    VALUES (1, 1, 1, 1, 'before-delete', CURRENT_TIMESTAMP)
                    """);
            }

            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    UPDATE mdm_child_record
                    SET status = 'deleted', deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = 1
                    """);
                statement.executeUpdate("UPDATE wf_approval_request SET status = 'APPROVED' WHERE id = 1");
            }
            connection.commit();

            assertThat(singleValue(connection, "SELECT status FROM mdm_child_record WHERE id = 1"))
                .isEqualTo("deleted");
            assertThat(singleValue(connection, "SELECT COUNT(*) FROM mdm_child_record_value WHERE child_record_id = 1"))
                .isEqualTo(1L);
            assertThat(singleValue(connection, "SELECT COUNT(*) FROM wf_approval_child_change WHERE child_record_id = 1"))
                .isEqualTo(1L);
            assertThat(singleValue(connection, "SELECT old_string_value FROM wf_approval_child_value_change WHERE id = 1"))
                .isEqualTo("before-delete");

            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM mdm_child_record WHERE id = 1");
                }
            }).isInstanceOf(SQLException.class);
            connection.rollback();
            assertThat(singleValue(connection, "SELECT COUNT(*) FROM wf_approval_child_change WHERE child_record_id = 1"))
                .isEqualTo(1L);
        }
    }

    private void insertV1Rows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO sys_system (id, code, name, status, created_at, updated_at) VALUES (1, 'S1', 'System 1', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO sys_department (id, system_id, code, name, level, path, status, created_at, updated_at) VALUES (1, 1, 'D1', 'Department 1', 1, '/1/', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO sys_user (id, system_id, department_id, username, password_hash, real_name, status, created_at, updated_at) VALUES (1, 1, 1, 'u1', 'hash', 'User 1', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO mdm_object_type (id, system_id, code, name, status, created_at, updated_at) VALUES (1, 1, 'PERSON', 'Person', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO mdm_child_type (id, system_id, object_type_id, code, name, status, created_at, updated_at) VALUES (1, 1, 1, 'JOB', 'Job', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO mdm_child_field_definition (id, system_id, child_type_id, field_key, field_name, data_type, status, created_at, updated_at) VALUES (1, 1, 1, 'title', 'Title', 'STRING', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO mdm_record (id, system_id, object_type_id, department_id, record_code, status, approval_status, created_at, updated_at) VALUES (1, 1, 1, 1, 'P-001', 'active', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO wf_approval_request (id, system_id, object_type_id, record_id, department_id, requested_by, expected_version, status, submitted_at, created_at, updated_at) VALUES (1, 1, 1, 1, 1, 1, 0, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO sys_push_endpoint (id, system_id, code, name, endpoint_url, authentication_type, status, created_at, updated_at) VALUES (1, 1, 'E1', 'Endpoint 1', 'https://example.test/push', 'NONE', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO sys_push_subscription (id, system_id, endpoint_id, object_type_id, event_type, status, created_at, updated_at) VALUES (1, 1, 1, 1, 'RECORD_CHANGED', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            if (hasColumn(connection, "SYS_PUSH_LOG", "IDEMPOTENCY_KEY")) {
                statement.executeUpdate("INSERT INTO sys_push_log (id, system_id, subscription_id, record_id, event_id, status, idempotency_key, active_dedup_key, created_at) VALUES (1, 1, 1, 1, 'event-1', 'PENDING', 'fixture:event-1', 'fixture:event-1', CURRENT_TIMESTAMP)");
            } else {
                statement.executeUpdate("INSERT INTO sys_push_log (id, system_id, subscription_id, record_id, event_id, status, created_at) VALUES (1, 1, 1, 1, 'event-1', 'pending', CURRENT_TIMESTAMP)");
            }
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet result = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return result.next();
        }
    }

    private void insertV2ApprovalChildFixture(Connection connection) throws SQLException {
        insertV1Rows(connection);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO mdm_child_record (id, system_id, record_id, child_type_id, status, created_at, updated_at) VALUES (1, 1, 1, 1, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("""
                INSERT INTO wf_approval_child_change
                    (id, system_id, approval_request_id, change_key, child_type_id, child_record_id,
                     operation, expected_version, created_at)
                VALUES (1, 1, 1, 'job-1', 1, 1, 'UPDATE', 0, CURRENT_TIMESTAMP)
                """);
        }
    }

    private void assertChildValueInsertSucceeds(Connection connection, String valueColumns, String values) throws SQLException {
        assertThat(insertChildValue(connection, valueColumns, values)).isEqualTo(1);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM wf_approval_child_value_change");
        }
    }

    private int insertChildValue(Connection connection, String valueColumns, String values) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("""
                INSERT INTO wf_approval_child_value_change
                    (system_id, approval_child_change_id, field_definition_id, %s)
                VALUES (1, 1, 1, %s)
                """.formatted(valueColumns, values));
        }
    }

    private Object singleValue(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getObject(1);
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

    private Set<Column> columns(Connection connection, String tableName) throws SQLException {
        Set<Column> columns = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, null, tableName, "%")) {
            while (result.next()) {
                columns.add(new Column(
                    result.getString("COLUMN_NAME"),
                    normalizedJdbcType(result.getInt("DATA_TYPE")),
                    result.getInt("NULLABLE") == DatabaseMetaData.columnNullable));
            }
        }
        return columns;
    }

    private int normalizedJdbcType(int jdbcType) {
        return switch (jdbcType) {
            case Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR -> Types.CHAR;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Types.TIMESTAMP;
            default -> jdbcType;
        };
    }

    private Column column(String name, int jdbcType, boolean nullable) {
        return new Column(name, jdbcType, nullable);
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

    private record Column(String name, int jdbcType, boolean nullable) {
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
