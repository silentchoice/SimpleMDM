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
}
