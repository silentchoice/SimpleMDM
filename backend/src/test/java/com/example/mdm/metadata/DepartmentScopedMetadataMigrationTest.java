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
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
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

  @Test
  void stopsBeforeDataChangesWhenLegacyDepartmentHasMultipleActiveTemplates() throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream("/db/migration/V3__department_scoped_metadata.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    assertThat(migration).contains("SIGNAL SQLSTATE '45000'");
    assertThat(migration).contains("Resolve the active template conflict for each department before retrying V3");
    assertThat(migration).doesNotContain("SET assignment.status = 'INACTIVE'");
    assertThat(migration.indexOf("CALL validate_department_master_type_assignments()"))
        .isLessThan(migration.indexOf("ALTER TABLE master_fields"));
  }

  @Test
  void createsForeignKeySupportIndexesBeforeDroppingLegacyUniqueIndexes() throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream("/db/migration/V3__department_scoped_metadata.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    assertThat(migration.indexOf("ADD INDEX idx_master_fields_master_type (master_type_id)"))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(migration.indexOf("DROP INDEX uk_master_fields_type_code"));
    assertThat(migration.indexOf("ADD INDEX idx_sub_types_master_type (master_type_id)"))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(migration.indexOf("DROP INDEX uk_sub_types_master_code"));
    assertThat(migration.indexOf("ADD INDEX idx_sub_fields_sub_type (sub_type_id)"))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(migration.indexOf("DROP INDEX uk_sub_fields_type_code"));
  }

  @Test
  void materializesUnscopedTemplatesBeforeWritingDepartmentScope() throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream("/db/migration/V3__department_scoped_metadata.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    assertThat(migration).contains("CREATE TEMPORARY TABLE metadata_unscoped_master_types AS");
    assertThat(migration).contains("FROM metadata_unscoped_master_types source\nJOIN departments legacy_department");
    assertThat(migration).doesNotContain(
        "INSERT INTO metadata_department_scope (master_type_id, department_id)\n"
            + "SELECT source.master_type_id, legacy_department.id\n"
            + "FROM metadata_source_master_types source");
  }

  @Test
  void materializesRetainedDepartmentBeforeCloningMetadata() throws IOException {
    String migration;
    try (var stream = getClass().getResourceAsStream("/db/migration/V3__department_scoped_metadata.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    assertThat(migration).contains("CREATE TEMPORARY TABLE metadata_retained_department_scope AS");
    assertThat(migration).contains("JOIN metadata_retained_department_scope retained");
    assertThat(migration).doesNotContain(
        "JOIN (\n  SELECT master_type_id, MIN(department_id) AS department_id\n"
            + "  FROM metadata_department_scope");
  }
}
