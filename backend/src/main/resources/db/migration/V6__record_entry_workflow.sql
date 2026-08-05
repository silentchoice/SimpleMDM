ALTER TABLE master_records
  DROP INDEX uk_master_records_type_code,
  ADD UNIQUE KEY uk_master_records_department_type_code (department_id, master_type_id, record_code);

ALTER TABLE master_record_drafts
  ADD COLUMN record_action VARCHAR(32) NOT NULL DEFAULT 'CREATE' AFTER record_code,
  ADD COLUMN base_version BIGINT NULL AFTER version,
  ADD COLUMN delete_reason VARCHAR(1000) NULL AFTER base_version,
  ADD COLUMN approval_task_id BIGINT NULL AFTER delete_reason,
  ADD COLUMN active_record_code VARCHAR(64) GENERATED ALWAYS AS
    (CASE WHEN status IN ('DRAFT','PENDING') THEN record_code ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_master_record_drafts_active
    (department_id, master_type_id, active_record_code),
  ADD CONSTRAINT fk_master_drafts_approval_task
    FOREIGN KEY (approval_task_id) REFERENCES approval_tasks (id);

ALTER TABLE sub_record_drafts
  ADD COLUMN row_order INT NOT NULL DEFAULT 0 AFTER sub_type_id;

ALTER TABLE sub_records
  ADD COLUMN row_order INT NOT NULL DEFAULT 0 AFTER sub_type_id;

CREATE TABLE master_type_code_rules (
  master_type_id BIGINT NOT NULL,
  pattern VARCHAR(255) NOT NULL,
  sequence_width INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (master_type_id),
  CONSTRAINT fk_master_type_code_rules_type
    FOREIGN KEY (master_type_id) REFERENCES master_types (id)
);

CREATE TABLE code_sequences (
  master_type_id BIGINT NOT NULL,
  sequence_date DATE NOT NULL,
  next_value BIGINT NOT NULL,
  PRIMARY KEY (master_type_id, sequence_date),
  CONSTRAINT fk_code_sequences_type
    FOREIGN KEY (master_type_id) REFERENCES master_types (id)
);
