CREATE TABLE departments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_departments_code (code)
);

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  department_id BIGINT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id)
);

CREATE TABLE roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_code (code)
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE master_types (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_master_types_code (code),
  CONSTRAINT fk_master_types_creator FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE department_master_types (
  department_id BIGINT NOT NULL,
  master_type_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  PRIMARY KEY (department_id, master_type_id),
  CONSTRAINT fk_department_master_types_department FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_department_master_types_type FOREIGN KEY (master_type_id) REFERENCES master_types (id)
);

CREATE TABLE master_fields (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_type_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  field_type VARCHAR(32) NOT NULL,
  required_flag BOOLEAN NOT NULL DEFAULT FALSE,
  options JSON NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_master_fields_type_code (master_type_id, code),
  CONSTRAINT fk_master_fields_type FOREIGN KEY (master_type_id) REFERENCES master_types (id)
);

CREATE TABLE sub_types (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_type_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sub_types_master_code (master_type_id, code),
  CONSTRAINT fk_sub_types_master_type FOREIGN KEY (master_type_id) REFERENCES master_types (id)
);

CREATE TABLE sub_fields (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sub_type_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  field_type VARCHAR(32) NOT NULL,
  required_flag BOOLEAN NOT NULL DEFAULT FALSE,
  options JSON NULL,
  share_config BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sub_fields_type_code (sub_type_id, code),
  CONSTRAINT fk_sub_fields_sub_type FOREIGN KEY (sub_type_id) REFERENCES sub_types (id)
);

CREATE TABLE master_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_type_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  record_code VARCHAR(64) NOT NULL,
  field_values JSON NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL,
  deleted_at DATETIME NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_master_records_type_code (master_type_id, record_code),
  CONSTRAINT fk_master_records_type FOREIGN KEY (master_type_id) REFERENCES master_types (id),
  CONSTRAINT fk_master_records_department FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_master_records_creator FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE sub_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_record_id BIGINT NOT NULL,
  sub_type_id BIGINT NOT NULL,
  field_values JSON NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL,
  deleted_at DATETIME NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_sub_records_master_record FOREIGN KEY (master_record_id) REFERENCES master_records (id),
  CONSTRAINT fk_sub_records_sub_type FOREIGN KEY (sub_type_id) REFERENCES sub_types (id),
  CONSTRAINT fk_sub_records_creator FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE master_record_drafts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_record_id BIGINT NULL,
  master_type_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  record_code VARCHAR(64) NOT NULL,
  field_values JSON NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_master_drafts_record FOREIGN KEY (master_record_id) REFERENCES master_records (id),
  CONSTRAINT fk_master_drafts_type FOREIGN KEY (master_type_id) REFERENCES master_types (id),
  CONSTRAINT fk_master_drafts_department FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_master_drafts_creator FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE sub_record_drafts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_draft_id BIGINT NOT NULL,
  sub_record_id BIGINT NULL,
  sub_type_id BIGINT NOT NULL,
  field_values JSON NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_sub_drafts_master_draft FOREIGN KEY (master_draft_id) REFERENCES master_record_drafts (id),
  CONSTRAINT fk_sub_drafts_record FOREIGN KEY (sub_record_id) REFERENCES sub_records (id),
  CONSTRAINT fk_sub_drafts_type FOREIGN KEY (sub_type_id) REFERENCES sub_types (id),
  CONSTRAINT fk_sub_drafts_creator FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE master_record_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  master_record_id BIGINT NOT NULL,
  version BIGINT NOT NULL,
  snapshot JSON NOT NULL,
  status VARCHAR(32) NOT NULL,
  changed_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_master_record_history_version (master_record_id, version),
  CONSTRAINT fk_master_history_record FOREIGN KEY (master_record_id) REFERENCES master_records (id),
  CONSTRAINT fk_master_history_actor FOREIGN KEY (changed_by) REFERENCES users (id)
);

CREATE TABLE approval_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  department_id BIGINT NOT NULL,
  entity_type VARCHAR(32) NOT NULL,
  entity_id BIGINT NOT NULL,
  before_snapshot JSON NULL,
  after_snapshot JSON NOT NULL,
  status VARCHAR(32) NOT NULL,
  submitted_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  review_comment VARCHAR(1000) NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_approval_tasks_department FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_approval_tasks_submitter FOREIGN KEY (submitted_by) REFERENCES users (id),
  CONSTRAINT fk_approval_tasks_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
);

CREATE TABLE edit_lock_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  entity_type VARCHAR(32) NOT NULL,
  entity_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_edit_lock_audit_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE sync_configs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  department_id BIGINT NOT NULL,
  master_type_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  endpoint_url VARCHAR(2048) NOT NULL,
  auth_type VARCHAR(32) NOT NULL,
  credential_ciphertext VARCHAR(4096) NULL,
  schedule_type VARCHAR(32) NOT NULL,
  cron_expression VARCHAR(255) NULL,
  sync_mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_sync_configs_department FOREIGN KEY (department_id) REFERENCES departments (id),
  CONSTRAINT fk_sync_configs_master_type FOREIGN KEY (master_type_id) REFERENCES master_types (id),
  CONSTRAINT fk_sync_configs_creator FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE sync_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sync_config_id BIGINT NOT NULL,
  request_snapshot JSON NOT NULL,
  response_body JSON NULL,
  http_status INT NULL,
  status VARCHAR(32) NOT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_sync_logs_config FOREIGN KEY (sync_config_id) REFERENCES sync_configs (id)
);

CREATE TABLE sync_retries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sync_log_id BIGINT NOT NULL,
  sync_config_id BIGINT NOT NULL,
  attempt INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  status VARCHAR(32) NOT NULL,
  stopped_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_sync_retries_log FOREIGN KEY (sync_log_id) REFERENCES sync_logs (id),
  CONSTRAINT fk_sync_retries_config FOREIGN KEY (sync_config_id) REFERENCES sync_configs (id),
  CONSTRAINT fk_sync_retries_stopper FOREIGN KEY (stopped_by) REFERENCES users (id)
);
