DROP PROCEDURE IF EXISTS preflight_record_draft_workflow_migration;

DELIMITER $$
CREATE PROCEDURE preflight_record_draft_workflow_migration()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'master_record_drafts'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'master_record_drafts'
      AND column_name = 'record_action'
  ) THEN
    SET @duplicate_active_record_drafts = 0;
    SET @duplicate_active_record_drafts_sql =
      'SELECT EXISTS (SELECT 1 FROM master_record_drafts '
      'WHERE status IN (''DRAFT'', ''PENDING'') '
      'GROUP BY department_id, master_type_id, record_code HAVING COUNT(*) > 1) '
      'INTO @duplicate_active_record_drafts';
    PREPARE duplicate_active_record_drafts_statement
      FROM @duplicate_active_record_drafts_sql;
    EXECUTE duplicate_active_record_drafts_statement;
    DEALLOCATE PREPARE duplicate_active_record_drafts_statement;

    IF @duplicate_active_record_drafts = 1 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Resolve duplicate active record drafts before retrying migration V6';
    END IF;
  END IF;
END$$
DELIMITER ;

CALL preflight_record_draft_workflow_migration();
DROP PROCEDURE preflight_record_draft_workflow_migration;
