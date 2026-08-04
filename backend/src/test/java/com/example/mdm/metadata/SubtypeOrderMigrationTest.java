package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SubtypeOrderMigrationTest {
  @Test void migrationBackfillsDeterministicDepartmentTemplateIdOrderBeforeNotNull() throws Exception {
    try (var stream = getClass().getResourceAsStream(
        "/db/migration/V5__ordered_department_subtypes.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql).contains("ADD COLUMN sort_order INT NULL")
          .contains("PARTITION BY department_id, master_type_id ORDER BY id")
          .contains("MODIFY COLUMN sort_order INT NOT NULL")
          .contains("department_id, master_type_id, sort_order, id");
      assertThat(sql.indexOf("ROW_NUMBER()"))
          .isLessThan(sql.indexOf("MODIFY COLUMN sort_order INT NOT NULL"));
      assertThat(sql).doesNotContain("UNIQUE KEY");
    }
  }
}
