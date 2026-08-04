package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DepartmentScopedMetadataMigrationTest {
  @Test
  void backfillsScopedMetadataBeforeMakingDepartmentColumnsRequired() throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream("/db/migration/V3__department_scoped_metadata.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(migration).contains("ADD COLUMN department_id BIGINT NULL");
    assertThat(migration).contains("CREATE TEMPORARY TABLE metadata_department_scope");
    assertThat(migration).contains("INSERT INTO master_fields");
    assertThat(migration).contains("INSERT INTO sub_types");
    assertThat(migration).contains("INSERT INTO sub_fields");
    assertThat(migration).contains("UPDATE sub_records");
    assertThat(migration.indexOf("UPDATE master_fields"))
        .isLessThan(migration.indexOf("MODIFY COLUMN department_id BIGINT NOT NULL"));
  }
}
