ALTER TABLE sys_push_log ADD COLUMN idempotency_key VARCHAR(160) NULL;
ALTER TABLE sys_push_log ADD COLUMN active_dedup_key VARCHAR(160) NULL;
ALTER TABLE sys_push_log ADD COLUMN cancelled_by BIGINT NULL;
ALTER TABLE sys_push_log ADD COLUMN cancelled_at DATETIME NULL;
ALTER TABLE sys_push_log ADD COLUMN cancellation_reason VARCHAR(512) NULL;

UPDATE sys_push_log
SET idempotency_key = CONCAT('legacy:', id),
    active_dedup_key = CONCAT('legacy:', id),
    status = CASE status
        WHEN 'pending' THEN 'PENDING'
        WHEN 'processing' THEN 'RUNNING'
        WHEN 'succeeded' THEN 'SUCCESS'
        WHEN 'failed' THEN 'FAILED'
        ELSE UPPER(status)
    END;

ALTER TABLE sys_push_log MODIFY COLUMN idempotency_key VARCHAR(160) NOT NULL;
ALTER TABLE sys_push_log ADD CONSTRAINT uk_push_log_idempotency UNIQUE (idempotency_key);
ALTER TABLE sys_push_log ADD CONSTRAINT uk_push_log_active_dedup UNIQUE (active_dedup_key);
ALTER TABLE sys_push_log ADD CONSTRAINT fk_push_log_cancelled_by_system
    FOREIGN KEY (system_id, cancelled_by) REFERENCES sys_user (system_id, id) ON DELETE RESTRICT;

ALTER TABLE sys_push_endpoint ADD COLUMN schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_push_endpoint ADD COLUMN schedule_cron VARCHAR(128) NULL;
ALTER TABLE sys_push_endpoint ADD COLUMN schedule_timezone VARCHAR(64) NULL;
ALTER TABLE sys_push_endpoint ADD COLUMN schedule_next_at DATETIME NULL;
ALTER TABLE sys_push_endpoint ADD COLUMN schedule_last_at DATETIME NULL;
