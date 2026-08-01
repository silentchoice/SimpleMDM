CREATE TABLE mdm_metadata_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_snapshot TEXT,
    after_snapshot TEXT,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_metadata_audit_system_id UNIQUE (system_id, id),
    CONSTRAINT fk_metadata_audit_system FOREIGN KEY (system_id) REFERENCES sys_system (id) ON DELETE RESTRICT,
    CONSTRAINT fk_metadata_audit_actor_system FOREIGN KEY (system_id, actor_id)
        REFERENCES sys_user (system_id, id) ON DELETE RESTRICT
);
