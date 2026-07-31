CREATE TABLE sys_system (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_system_code UNIQUE (code)
);

CREATE TABLE sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, parent_id BIGINT, code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL, level INTEGER NOT NULL, path VARCHAR(2048) NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_department_code UNIQUE (system_id, code), CONSTRAINT uk_department_parent_name UNIQUE (system_id, parent_id, name),
    CONSTRAINT fk_department_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department (id) ON DELETE RESTRICT
);

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, department_id BIGINT NOT NULL, username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL, real_name VARCHAR(128) NOT NULL, email VARCHAR(255), mobile VARCHAR(64), status VARCHAR(32) NOT NULL,
    is_system_admin BOOLEAN NOT NULL DEFAULT FALSE, failed_login_count INTEGER NOT NULL DEFAULT 0, locked_until DATETIME, last_login_at DATETIME,
    password_changed_at DATETIME, created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0, deleted_at DATETIME, CONSTRAINT uk_user_username UNIQUE (system_id, username),
    CONSTRAINT fk_user_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    description VARCHAR(512), status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_code UNIQUE (system_id, code),
    CONSTRAINT fk_role_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT
);

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(128) NOT NULL, name VARCHAR(128) NOT NULL, description VARCHAR(512),
    status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE sys_user_role (
    system_id BIGINT NOT NULL, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, created_at DATETIME NOT NULL, PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL, created_at DATETIME NOT NULL, PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE
);

CREATE TABLE sys_user_department_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, user_id BIGINT NOT NULL, department_id BIGINT NOT NULL, scope_mode VARCHAR(16) NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT FALSE, can_edit BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    CONSTRAINT uk_user_department_scope UNIQUE (user_id, department_id, scope_mode),
    CONSTRAINT fk_scope_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_scope_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_object_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL, department_scoped BOOLEAN NOT NULL DEFAULT TRUE, approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_object_type_code UNIQUE (system_id, code),
    CONSTRAINT fk_object_type_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_object_type_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_object_type_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_field_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, object_type_id BIGINT NOT NULL, field_key VARCHAR(64) NOT NULL, field_name VARCHAR(128) NOT NULL,
    data_type VARCHAR(16) NOT NULL, required BOOLEAN NOT NULL DEFAULT FALSE, unique_value BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT FALSE, shared BOOLEAN NOT NULL DEFAULT FALSE, max_length INTEGER, precision_value INTEGER, scale_value INTEGER,
    reference_object_type_id BIGINT, default_value VARCHAR(2048), validation_rule VARCHAR(2048), sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_field_definition_key UNIQUE (object_type_id, field_key),
    CONSTRAINT fk_field_definition_type FOREIGN KEY (object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_field_definition_reference FOREIGN KEY (reference_object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_field_definition_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_field_definition_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_child_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, object_type_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    description VARCHAR(512), sort_order INTEGER NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL,
    created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_child_type_code UNIQUE (object_type_id, code),
    CONSTRAINT fk_child_type_object FOREIGN KEY (object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_type_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_type_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_child_field_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, child_type_id BIGINT NOT NULL, field_key VARCHAR(64) NOT NULL, field_name VARCHAR(128) NOT NULL,
    data_type VARCHAR(16) NOT NULL, required BOOLEAN NOT NULL DEFAULT FALSE, unique_value BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT FALSE, max_length INTEGER, precision_value INTEGER, scale_value INTEGER, reference_object_type_id BIGINT,
    default_value VARCHAR(2048), validation_rule VARCHAR(2048), sort_order INTEGER NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_child_field_definition_key UNIQUE (child_type_id, field_key),
    CONSTRAINT fk_child_field_definition_type FOREIGN KEY (child_type_id) REFERENCES mdm_child_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_field_definition_ref FOREIGN KEY (reference_object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_field_definition_created FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_field_definition_updated FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, object_type_id BIGINT NOT NULL, department_id BIGINT NOT NULL,
    record_code VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, approval_status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL,
    created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0, deleted_at DATETIME,
    active_record_code VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN record_code ELSE NULL END),
    CONSTRAINT uk_record_code UNIQUE (object_type_id, active_record_code),
    CONSTRAINT fk_record_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_object_type FOREIGN KEY (object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_record_value (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, record_id BIGINT NOT NULL, field_definition_id BIGINT NOT NULL, string_value VARCHAR(4096), text_value TEXT,
    integer_value BIGINT, decimal_value DECIMAL(38,10), boolean_value BOOLEAN, date_value DATE, datetime_value DATETIME, reference_record_id BIGINT,
    created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_record_field UNIQUE (record_id, field_definition_id),
    CONSTRAINT ck_record_value_one_type CHECK ((CASE WHEN string_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN text_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN integer_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN decimal_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN boolean_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN date_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN datetime_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN reference_record_id IS NOT NULL THEN 1 ELSE 0 END) <= 1),
    CONSTRAINT fk_record_value_record FOREIGN KEY (record_id) REFERENCES mdm_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_record_value_field FOREIGN KEY (field_definition_id) REFERENCES mdm_field_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_value_reference FOREIGN KEY (reference_record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_value_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_value_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_child_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, record_id BIGINT NOT NULL, child_type_id BIGINT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0, deleted_at DATETIME,
    CONSTRAINT fk_child_record_record FOREIGN KEY (record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_record_type FOREIGN KEY (child_type_id) REFERENCES mdm_child_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE mdm_child_record_value (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, child_record_id BIGINT NOT NULL, field_definition_id BIGINT NOT NULL, string_value VARCHAR(4096), text_value TEXT,
    integer_value BIGINT, decimal_value DECIMAL(38,10), boolean_value BOOLEAN, date_value DATE, datetime_value DATETIME, reference_record_id BIGINT,
    created_at DATETIME NOT NULL, created_by BIGINT, updated_at DATETIME NOT NULL, updated_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_child_record_field UNIQUE (child_record_id, field_definition_id),
    CONSTRAINT ck_child_record_value_one_type CHECK ((CASE WHEN string_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN text_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN integer_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN decimal_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN boolean_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN date_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN datetime_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN reference_record_id IS NOT NULL THEN 1 ELSE 0 END) <= 1),
    CONSTRAINT fk_child_value_record FOREIGN KEY (child_record_id) REFERENCES mdm_child_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_child_value_field FOREIGN KEY (field_definition_id) REFERENCES mdm_child_field_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_value_reference FOREIGN KEY (reference_record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_value_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_value_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE wf_approval_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, object_type_id BIGINT NOT NULL, record_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL, requested_by BIGINT NOT NULL, expected_version BIGINT NOT NULL, status VARCHAR(32) NOT NULL,
    submitted_at DATETIME NOT NULL, decided_at DATETIME, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_approval_request_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_request_type FOREIGN KEY (object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_request_record FOREIGN KEY (record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_request_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_request_user FOREIGN KEY (requested_by) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE wf_approval_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, approval_request_id BIGINT NOT NULL, field_definition_id BIGINT NOT NULL,
    old_string_value VARCHAR(4096), old_text_value TEXT, old_integer_value BIGINT, old_decimal_value DECIMAL(38,10), old_boolean_value BOOLEAN, old_date_value DATE, old_datetime_value DATETIME, old_reference_record_id BIGINT,
    new_string_value VARCHAR(4096), new_text_value TEXT, new_integer_value BIGINT, new_decimal_value DECIMAL(38,10), new_boolean_value BOOLEAN, new_date_value DATE, new_datetime_value DATETIME, new_reference_record_id BIGINT,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_approval_change_field UNIQUE (approval_request_id, field_definition_id),
    CONSTRAINT ck_approval_change_old_one_type CHECK ((CASE WHEN old_string_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_text_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_integer_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_decimal_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_boolean_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_date_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_datetime_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN old_reference_record_id IS NOT NULL THEN 1 ELSE 0 END) <= 1),
    CONSTRAINT ck_approval_change_new_one_type CHECK ((CASE WHEN new_string_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_text_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_integer_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_decimal_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_boolean_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_date_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_datetime_value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN new_reference_record_id IS NOT NULL THEN 1 ELSE 0 END) <= 1),
    CONSTRAINT fk_approval_change_request FOREIGN KEY (approval_request_id) REFERENCES wf_approval_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_change_field FOREIGN KEY (field_definition_id) REFERENCES mdm_field_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_change_old_ref FOREIGN KEY (old_reference_record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_change_new_ref FOREIGN KEY (new_reference_record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT
);

CREATE TABLE wf_approval_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, approval_request_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL,
    comment VARCHAR(2048), acted_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_action_request FOREIGN KEY (approval_request_id) REFERENCES wf_approval_request (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_action_actor FOREIGN KEY (actor_id) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE sys_approver_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, object_type_id BIGINT, department_id BIGINT NOT NULL,
    approver_user_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    CONSTRAINT uk_approver_assignment UNIQUE (system_id, object_type_id, department_id, approver_user_id),
    CONSTRAINT fk_approver_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approver_type FOREIGN KEY (object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approver_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE RESTRICT,
    CONSTRAINT fk_approver_user FOREIGN KEY (approver_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT
);

CREATE TABLE sys_push_endpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    endpoint_url VARCHAR(2048) NOT NULL, authentication_type VARCHAR(32) NOT NULL, encrypted_credentials VARCHAR(4096), status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_push_endpoint_code UNIQUE (system_id, code),
    CONSTRAINT fk_push_endpoint_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT
);

CREATE TABLE sys_push_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, endpoint_id BIGINT NOT NULL, object_type_id BIGINT,
    event_type VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    CONSTRAINT uk_push_subscription UNIQUE (endpoint_id, object_type_id, event_type),
    CONSTRAINT fk_push_subscription_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_push_subscription_endpoint FOREIGN KEY (endpoint_id) REFERENCES sys_push_endpoint (id) ON DELETE RESTRICT,
    CONSTRAINT fk_push_subscription_type FOREIGN KEY (object_type_id) REFERENCES mdm_object_type (id) ON DELETE RESTRICT
);

CREATE TABLE sys_push_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, system_id BIGINT NOT NULL, subscription_id BIGINT NOT NULL, record_id BIGINT, event_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0, request_snapshot TEXT, response_snapshot TEXT, last_attempt_at DATETIME,
    created_at DATETIME NOT NULL, CONSTRAINT uk_push_log_event UNIQUE (subscription_id, event_id),
    CONSTRAINT fk_push_log_subscription FOREIGN KEY (subscription_id) REFERENCES sys_push_subscription (id) ON DELETE RESTRICT,
    CONSTRAINT fk_push_log_record FOREIGN KEY (record_id) REFERENCES mdm_record (id) ON DELETE RESTRICT
);

ALTER TABLE sys_department ADD CONSTRAINT uk_department_system_id UNIQUE (system_id, id);
ALTER TABLE mdm_object_type ADD CONSTRAINT uk_object_type_system_id UNIQUE (system_id, id);
ALTER TABLE sys_push_endpoint ADD CONSTRAINT uk_push_endpoint_system_id UNIQUE (system_id, id);

ALTER TABLE sys_department ADD CONSTRAINT fk_department_parent_system FOREIGN KEY (system_id, parent_id) REFERENCES sys_department (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_user ADD CONSTRAINT fk_user_department_system FOREIGN KEY (system_id, department_id) REFERENCES sys_department (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record ADD CONSTRAINT fk_record_object_system FOREIGN KEY (system_id, object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record ADD CONSTRAINT fk_record_department_system FOREIGN KEY (system_id, department_id) REFERENCES sys_department (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_request ADD CONSTRAINT fk_approval_object_system FOREIGN KEY (system_id, object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_request ADD CONSTRAINT fk_approval_department_system FOREIGN KEY (system_id, department_id) REFERENCES sys_department (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_approver_assignment ADD CONSTRAINT fk_approver_object_system FOREIGN KEY (system_id, object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_approver_assignment ADD CONSTRAINT fk_approver_department_system FOREIGN KEY (system_id, department_id) REFERENCES sys_department (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_push_subscription ADD CONSTRAINT fk_subscription_endpoint_system FOREIGN KEY (system_id, endpoint_id) REFERENCES sys_push_endpoint (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_push_subscription ADD CONSTRAINT fk_subscription_object_system FOREIGN KEY (system_id, object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;

ALTER TABLE sys_user ADD CONSTRAINT uk_user_system_id UNIQUE (system_id, id);
ALTER TABLE sys_role ADD CONSTRAINT uk_role_system_id UNIQUE (system_id, id);
ALTER TABLE mdm_record ADD CONSTRAINT uk_record_system_id UNIQUE (system_id, id);
ALTER TABLE wf_approval_request ADD CONSTRAINT uk_approval_request_system_id UNIQUE (system_id, id);
ALTER TABLE sys_push_subscription ADD CONSTRAINT uk_push_subscription_system_id UNIQUE (system_id, id);

ALTER TABLE sys_user ADD CONSTRAINT fk_user_created_by_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_user ADD CONSTRAINT fk_user_updated_by_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_user_system FOREIGN KEY (system_id, user_id) REFERENCES sys_user (system_id, id) ON DELETE CASCADE;
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_role_system FOREIGN KEY (system_id, role_id) REFERENCES sys_role (system_id, id) ON DELETE CASCADE;
ALTER TABLE sys_user_department_scope ADD CONSTRAINT fk_scope_user_system FOREIGN KEY (system_id, user_id) REFERENCES sys_user (system_id, id) ON DELETE CASCADE;
ALTER TABLE sys_user_department_scope ADD CONSTRAINT fk_scope_department_system FOREIGN KEY (system_id, department_id) REFERENCES sys_department (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record ADD CONSTRAINT fk_record_created_by_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record ADD CONSTRAINT fk_record_updated_by_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_request ADD CONSTRAINT fk_approval_record_system FOREIGN KEY (system_id, record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_request ADD CONSTRAINT fk_approval_user_system FOREIGN KEY (system_id, requested_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_action ADD CONSTRAINT fk_approval_action_request_system FOREIGN KEY (system_id, approval_request_id) REFERENCES wf_approval_request (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_action ADD CONSTRAINT fk_approval_action_actor_system FOREIGN KEY (system_id, actor_id) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_approver_assignment ADD CONSTRAINT fk_approver_user_system FOREIGN KEY (system_id, approver_user_id) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_push_log ADD CONSTRAINT fk_push_log_subscription_system FOREIGN KEY (system_id, subscription_id) REFERENCES sys_push_subscription (system_id, id) ON DELETE RESTRICT;
ALTER TABLE sys_push_log ADD CONSTRAINT fk_push_log_record_system FOREIGN KEY (system_id, record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_object_type ADD CONSTRAINT fk_object_type_created_by_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_object_type ADD CONSTRAINT fk_object_type_updated_by_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_field_definition ADD COLUMN system_id BIGINT NOT NULL;
ALTER TABLE mdm_child_type ADD COLUMN system_id BIGINT NOT NULL;
ALTER TABLE mdm_child_field_definition ADD COLUMN system_id BIGINT NOT NULL;
ALTER TABLE mdm_record_value ADD COLUMN system_id BIGINT NOT NULL;
ALTER TABLE mdm_child_record ADD COLUMN system_id BIGINT NOT NULL;
ALTER TABLE mdm_child_record_value ADD COLUMN system_id BIGINT NOT NULL;
ALTER TABLE wf_approval_change ADD COLUMN system_id BIGINT NOT NULL;

ALTER TABLE mdm_field_definition ADD CONSTRAINT uk_field_system_id UNIQUE (system_id, id);
ALTER TABLE mdm_child_type ADD CONSTRAINT uk_child_type_system_id UNIQUE (system_id, id);
ALTER TABLE mdm_child_field_definition ADD CONSTRAINT uk_child_field_system_id UNIQUE (system_id, id);
ALTER TABLE mdm_child_record ADD CONSTRAINT uk_child_record_system_id UNIQUE (system_id, id);

ALTER TABLE mdm_field_definition ADD CONSTRAINT fk_field_object_system FOREIGN KEY (system_id, object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_field_definition ADD CONSTRAINT fk_field_reference_system FOREIGN KEY (system_id, reference_object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_field_definition ADD CONSTRAINT fk_field_created_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_field_definition ADD CONSTRAINT fk_field_updated_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_type ADD CONSTRAINT fk_child_type_object_system FOREIGN KEY (system_id, object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_type ADD CONSTRAINT fk_child_type_created_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_type ADD CONSTRAINT fk_child_type_updated_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_field_definition ADD CONSTRAINT fk_child_field_type_system FOREIGN KEY (system_id, child_type_id) REFERENCES mdm_child_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_field_definition ADD CONSTRAINT fk_child_field_reference_system FOREIGN KEY (system_id, reference_object_type_id) REFERENCES mdm_object_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_field_definition ADD CONSTRAINT fk_child_field_created_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_field_definition ADD CONSTRAINT fk_child_field_updated_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record_value ADD CONSTRAINT fk_record_value_record_system FOREIGN KEY (system_id, record_id) REFERENCES mdm_record (system_id, id) ON DELETE CASCADE;
ALTER TABLE mdm_record_value ADD CONSTRAINT fk_record_value_field_system FOREIGN KEY (system_id, field_definition_id) REFERENCES mdm_field_definition (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record_value ADD CONSTRAINT fk_record_value_reference_system FOREIGN KEY (system_id, reference_record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record_value ADD CONSTRAINT fk_record_value_created_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_record_value ADD CONSTRAINT fk_record_value_updated_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record ADD CONSTRAINT fk_child_record_record_system FOREIGN KEY (system_id, record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record ADD CONSTRAINT fk_child_record_type_system FOREIGN KEY (system_id, child_type_id) REFERENCES mdm_child_type (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record_value ADD CONSTRAINT fk_child_value_record_system FOREIGN KEY (system_id, child_record_id) REFERENCES mdm_child_record (system_id, id) ON DELETE CASCADE;
ALTER TABLE mdm_child_record_value ADD CONSTRAINT fk_child_value_field_system FOREIGN KEY (system_id, field_definition_id) REFERENCES mdm_child_field_definition (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record_value ADD CONSTRAINT fk_child_value_reference_system FOREIGN KEY (system_id, reference_record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_change ADD CONSTRAINT fk_approval_change_request_system FOREIGN KEY (system_id, approval_request_id) REFERENCES wf_approval_request (system_id, id) ON DELETE CASCADE;
ALTER TABLE wf_approval_change ADD CONSTRAINT fk_approval_change_field_system FOREIGN KEY (system_id, field_definition_id) REFERENCES mdm_field_definition (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_change ADD CONSTRAINT fk_approval_change_old_ref_system FOREIGN KEY (system_id, old_reference_record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE wf_approval_change ADD CONSTRAINT fk_approval_change_new_ref_system FOREIGN KEY (system_id, new_reference_record_id) REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record ADD CONSTRAINT fk_child_record_created_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record ADD CONSTRAINT fk_child_record_updated_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record_value ADD CONSTRAINT fk_child_value_created_system FOREIGN KEY (system_id, created_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
ALTER TABLE mdm_child_record_value ADD CONSTRAINT fk_child_value_updated_system FOREIGN KEY (system_id, updated_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;