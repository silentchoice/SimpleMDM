UPDATE master_record_drafts draft
JOIN master_records record ON record.id = draft.master_record_id
SET draft.base_version = record.version
WHERE draft.base_version IS NULL
  AND draft.record_action IN ('UPDATE', 'DELETE');

UPDATE master_record_drafts
SET base_version = 0
WHERE base_version IS NULL
  AND record_action = 'CREATE';

DROP PROCEDURE IF EXISTS validate_record_draft_base_versions;

DELIMITER $$
CREATE PROCEDURE validate_record_draft_base_versions()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM master_record_drafts
    WHERE base_version IS NULL
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Resolve record drafts without a matching base record before retrying migration V8';
  END IF;
END$$
DELIMITER ;

CALL validate_record_draft_base_versions();
DROP PROCEDURE validate_record_draft_base_versions;

ALTER TABLE master_record_drafts
  MODIFY COLUMN base_version BIGINT NOT NULL;
