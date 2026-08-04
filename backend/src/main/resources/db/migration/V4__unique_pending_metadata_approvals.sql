ALTER TABLE approval_tasks
  ADD COLUMN pending_metadata_key VARCHAR(255)
    GENERATED ALWAYS AS (
      CASE
        WHEN status = 'PENDING'
          AND entity_type IN ('MASTER_FIELDS', 'SUB_TYPES', 'SUB_FIELDS')
        THEN CONCAT(
          CAST(department_id AS CHAR), '|', entity_type, '|',
          COALESCE(CAST(entity_id AS CHAR), '<NULL>')
        )
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY uk_approval_tasks_pending_metadata (pending_metadata_key);
