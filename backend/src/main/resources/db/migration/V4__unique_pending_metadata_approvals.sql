DROP PROCEDURE IF EXISTS validate_pending_metadata_approvals;

DELIMITER $$
CREATE PROCEDURE validate_pending_metadata_approvals()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM approval_tasks
    WHERE status = 'PENDING'
      AND entity_type IN ('MASTER_FIELDS', 'SUB_TYPES', 'SUB_FIELDS')
    GROUP BY department_id, entity_type, COALESCE(entity_id, -1)
    HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Resolve duplicate pending metadata approvals before retrying V4';
  END IF;
END$$
DELIMITER ;

CALL validate_pending_metadata_approvals();
DROP PROCEDURE validate_pending_metadata_approvals;

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
