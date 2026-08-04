package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PendingMetadataApprovalMigrationTest {
  @Test void enforcesOnePendingTaskPerDepartmentKindAndEntityInTheDatabase() throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream(
        "/db/migration/V4__unique_pending_metadata_approvals.sql")) {
      assertThat(stream).isNotNull();
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(migration).contains("status = 'PENDING'");
    assertThat(migration).contains("department_id", "entity_type", "entity_id");
    assertThat(migration).contains("UNIQUE KEY uk_approval_tasks_pending_metadata");
    assertThat(migration).contains("pending_metadata_key");
  }

  @Test void duplicatePreflightFailsClearlyBeforeSchemaMutationWithoutDeletingData()
      throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream(
        "/db/migration/V4__unique_pending_metadata_approvals.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(migration).contains("SIGNAL SQLSTATE '45000'");
    assertThat(migration).contains("Resolve duplicate pending metadata approvals before retrying V4");
    assertThat(migration.indexOf("CALL validate_pending_metadata_approvals()"))
        .isLessThan(migration.indexOf("ALTER TABLE approval_tasks"));
    assertThat(migration).doesNotContainIgnoringCase("DELETE FROM approval_tasks");
    assertThat(migration).doesNotContainIgnoringCase("UPDATE approval_tasks");
  }
}
