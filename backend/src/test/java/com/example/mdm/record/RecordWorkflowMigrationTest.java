package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RecordWorkflowMigrationTest {
  @Test void migrationAddsRecordWorkflowColumnsConstraintsAndCodeRuleStorage() throws Exception {
    try (var stream = getClass().getResourceAsStream("/db/migration/V6__record_entry_workflow.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql).contains("record_action", "base_version", "delete_reason", "approval_task_id")
          .contains("row_order")
          .contains("active_record_code VARCHAR(64) GENERATED ALWAYS AS")
          .contains("status IN ('DRAFT','PENDING')")
          .contains("uk_master_record_drafts_active")
          .contains("department_id, master_type_id, active_record_code")
          .contains("CREATE TABLE master_type_code_rules")
          .contains("CREATE TABLE code_sequences")
          .contains("PRIMARY KEY (master_type_id, sequence_date)")
          .contains("sequence_date DATE NOT NULL")
          .contains("uk_master_records_department_type_code")
          .contains("FOREIGN KEY (master_type_id) REFERENCES master_types (id)")
          .contains("FOREIGN KEY (approval_task_id) REFERENCES approval_tasks (id)");
    }
  }
}
