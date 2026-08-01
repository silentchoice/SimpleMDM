ALTER TABLE mdm_child_field_definition
    ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE wf_approval_request
    ADD COLUMN operation VARCHAR(16) NOT NULL DEFAULT 'UPDATE';
ALTER TABLE wf_approval_request
    ADD COLUMN record_code VARCHAR(128) NULL;
ALTER TABLE wf_approval_request
    MODIFY COLUMN record_id BIGINT NULL;
ALTER TABLE wf_approval_request
    MODIFY COLUMN expected_version BIGINT NULL;

CREATE TABLE wf_approval_child_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id BIGINT NOT NULL,
    approval_request_id BIGINT NOT NULL,
    change_key VARCHAR(128) NOT NULL,
    child_type_id BIGINT NOT NULL,
    child_record_id BIGINT,
    operation VARCHAR(16) NOT NULL,
    expected_version BIGINT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_approval_child_change_key UNIQUE (approval_request_id, change_key),
    CONSTRAINT uk_approval_child_system_id UNIQUE (system_id, id),
    CONSTRAINT fk_approval_child_request_system FOREIGN KEY (system_id, approval_request_id)
        REFERENCES wf_approval_request (system_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_child_type_system FOREIGN KEY (system_id, child_type_id)
        REFERENCES mdm_child_type (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_child_record_system FOREIGN KEY (system_id, child_record_id)
        REFERENCES mdm_child_record (system_id, id) ON DELETE RESTRICT
);

CREATE TABLE wf_approval_child_value_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id BIGINT NOT NULL,
    approval_child_change_id BIGINT NOT NULL,
    field_definition_id BIGINT NOT NULL,
    old_string_value VARCHAR(4096),
    old_text_value TEXT,
    old_integer_value BIGINT,
    old_decimal_value DECIMAL(38,10),
    old_boolean_value BOOLEAN,
    old_date_value DATE,
    old_datetime_value DATETIME,
    old_reference_record_id BIGINT,
    new_string_value VARCHAR(4096),
    new_text_value TEXT,
    new_integer_value BIGINT,
    new_decimal_value DECIMAL(38,10),
    new_boolean_value BOOLEAN,
    new_date_value DATE,
    new_datetime_value DATETIME,
    new_reference_record_id BIGINT,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_approval_child_value_field UNIQUE (approval_child_change_id, field_definition_id),
    CONSTRAINT ck_approval_child_value_old_one_type CHECK (
        (CASE WHEN old_string_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_text_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_integer_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_decimal_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_boolean_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_date_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_datetime_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN old_reference_record_id IS NOT NULL THEN 1 ELSE 0 END) <= 1
    ),
    CONSTRAINT ck_approval_child_value_new_one_type CHECK (
        (CASE WHEN new_string_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_text_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_integer_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_decimal_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_boolean_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_date_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_datetime_value IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN new_reference_record_id IS NOT NULL THEN 1 ELSE 0 END) <= 1
    ),
    CONSTRAINT fk_approval_child_value_change_system FOREIGN KEY (system_id, approval_child_change_id)
        REFERENCES wf_approval_child_change (system_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_child_value_field_system FOREIGN KEY (system_id, field_definition_id)
        REFERENCES mdm_child_field_definition (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_child_value_old_ref_system FOREIGN KEY (system_id, old_reference_record_id)
        REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_child_value_new_ref_system FOREIGN KEY (system_id, new_reference_record_id)
        REFERENCES mdm_record (system_id, id) ON DELETE RESTRICT
);

ALTER TABLE sys_push_log
    ADD COLUMN trigger_type VARCHAR(16) NOT NULL DEFAULT 'AUTOMATIC';
ALTER TABLE sys_push_log
    ADD COLUMN triggered_by BIGINT NULL;
ALTER TABLE sys_push_log
    ADD COLUMN trigger_reason VARCHAR(512) NULL;
ALTER TABLE sys_push_log
    MODIFY COLUMN request_snapshot MEDIUMTEXT NULL;
UPDATE sys_push_endpoint
SET endpoint_url = CONCAT(
        SUBSTRING(endpoint_url, 1, LOCATE('://', endpoint_url) + 2),
        SUBSTRING(endpoint_url, LOCATE('@', endpoint_url, LOCATE('://', endpoint_url) + 3) + 1)
    ),
    status = 'inactive',
    updated_at = CURRENT_TIMESTAMP
WHERE REGEXP_LIKE(endpoint_url, '^[A-Za-z][A-Za-z0-9+.-]*://[^/?#]*@');
ALTER TABLE sys_push_log
    ADD CONSTRAINT fk_push_log_triggered_by_system FOREIGN KEY (system_id, triggered_by)
        REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
