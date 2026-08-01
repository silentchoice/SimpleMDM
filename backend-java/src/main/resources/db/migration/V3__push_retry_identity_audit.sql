ALTER TABLE sys_push_log
    ADD COLUMN last_retry_by BIGINT NULL;
ALTER TABLE sys_push_log
    ADD COLUMN last_retry_reason VARCHAR(512) NULL;
ALTER TABLE sys_push_log
    ADD COLUMN last_retry_at DATETIME NULL;
ALTER TABLE sys_push_log
    ADD CONSTRAINT fk_push_log_last_retry_by_system FOREIGN KEY (system_id, last_retry_by)
        REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;
