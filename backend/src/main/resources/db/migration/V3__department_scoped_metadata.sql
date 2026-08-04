DROP PROCEDURE IF EXISTS validate_department_master_type_assignments;

DELIMITER $$
CREATE PROCEDURE validate_department_master_type_assignments()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM department_master_types
    WHERE status = 'ACTIVE'
    GROUP BY department_id
    HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Resolve the active template conflict for each department before retrying V3';
  END IF;
END$$
DELIMITER ;

CALL validate_department_master_type_assignments();
DROP PROCEDURE validate_department_master_type_assignments;

ALTER TABLE master_fields
  ADD COLUMN department_id BIGINT NULL AFTER master_type_id,
  DROP INDEX uk_master_fields_type_code;

ALTER TABLE sub_types
  ADD COLUMN department_id BIGINT NULL AFTER master_type_id,
  DROP INDEX uk_sub_types_master_code;

ALTER TABLE sub_fields
  ADD COLUMN department_id BIGINT NULL AFTER sub_type_id,
  DROP INDEX uk_sub_fields_type_code;

CREATE TEMPORARY TABLE metadata_source_master_types (
  master_type_id BIGINT NOT NULL,
  PRIMARY KEY (master_type_id)
);

INSERT IGNORE INTO metadata_source_master_types (master_type_id)
SELECT master_type_id FROM master_fields;

INSERT IGNORE INTO metadata_source_master_types (master_type_id)
SELECT master_type_id FROM sub_types;

CREATE TEMPORARY TABLE metadata_department_scope (
  master_type_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  PRIMARY KEY (master_type_id, department_id)
);

INSERT IGNORE INTO metadata_department_scope (master_type_id, department_id)
SELECT assignment.master_type_id, assignment.department_id
FROM department_master_types assignment
JOIN metadata_source_master_types source ON source.master_type_id = assignment.master_type_id;

INSERT IGNORE INTO metadata_department_scope (master_type_id, department_id)
SELECT record.master_type_id, record.department_id
FROM master_records record
JOIN metadata_source_master_types source ON source.master_type_id = record.master_type_id;

INSERT IGNORE INTO metadata_department_scope (master_type_id, department_id)
SELECT draft.master_type_id, draft.department_id
FROM master_record_drafts draft
JOIN metadata_source_master_types source ON source.master_type_id = draft.master_type_id;

INSERT INTO departments (code, name, status)
SELECT '__LEGACY_METADATA_MIGRATION__', 'Legacy Metadata Migration', 'INACTIVE'
WHERE EXISTS (
  SELECT 1
  FROM metadata_source_master_types source
  LEFT JOIN metadata_department_scope scoped ON scoped.master_type_id = source.master_type_id
  WHERE scoped.master_type_id IS NULL
)
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

INSERT INTO metadata_department_scope (master_type_id, department_id)
SELECT source.master_type_id, legacy_department.id
FROM metadata_source_master_types source
JOIN departments legacy_department ON legacy_department.code = '__LEGACY_METADATA_MIGRATION__'
LEFT JOIN metadata_department_scope scoped ON scoped.master_type_id = source.master_type_id
WHERE scoped.master_type_id IS NULL;

INSERT INTO department_master_types (department_id, master_type_id, status)
SELECT scoped.department_id, scoped.master_type_id, 'INACTIVE'
FROM metadata_department_scope scoped
LEFT JOIN department_master_types assignment
  ON assignment.department_id = scoped.department_id
  AND assignment.master_type_id = scoped.master_type_id
WHERE assignment.department_id IS NULL;

CREATE TEMPORARY TABLE metadata_master_field_source AS
SELECT * FROM master_fields;

CREATE TEMPORARY TABLE metadata_sub_type_source AS
SELECT * FROM sub_types;

CREATE TEMPORARY TABLE metadata_sub_field_source AS
SELECT * FROM sub_fields;

UPDATE master_fields field_definition
JOIN (
  SELECT master_type_id, MIN(department_id) AS department_id
  FROM metadata_department_scope
  GROUP BY master_type_id
) scoped ON scoped.master_type_id = field_definition.master_type_id
SET field_definition.department_id = scoped.department_id;

INSERT INTO master_fields (master_type_id, department_id, code, display_name, field_type, required_flag,
    options, sort_order, status, created_at, updated_at)
SELECT source.master_type_id, scoped.department_id, source.code, source.display_name, source.field_type,
    source.required_flag, source.options, source.sort_order, source.status, source.created_at, source.updated_at
FROM metadata_master_field_source source
JOIN metadata_department_scope scoped ON scoped.master_type_id = source.master_type_id
JOIN (
  SELECT master_type_id, MIN(department_id) AS department_id
  FROM metadata_department_scope
  GROUP BY master_type_id
) retained ON retained.master_type_id = source.master_type_id
WHERE scoped.department_id <> retained.department_id;

UPDATE sub_types subtype_definition
JOIN (
  SELECT master_type_id, MIN(department_id) AS department_id
  FROM metadata_department_scope
  GROUP BY master_type_id
) scoped ON scoped.master_type_id = subtype_definition.master_type_id
SET subtype_definition.department_id = scoped.department_id;

INSERT INTO sub_types (master_type_id, department_id, code, name, status, created_at, updated_at)
SELECT source.master_type_id, scoped.department_id, source.code, source.name, source.status,
    source.created_at, source.updated_at
FROM metadata_sub_type_source source
JOIN metadata_department_scope scoped ON scoped.master_type_id = source.master_type_id
JOIN (
  SELECT master_type_id, MIN(department_id) AS department_id
  FROM metadata_department_scope
  GROUP BY master_type_id
) retained ON retained.master_type_id = source.master_type_id
WHERE scoped.department_id <> retained.department_id;

UPDATE sub_fields field_definition
JOIN metadata_sub_field_source source ON source.id = field_definition.id
JOIN sub_types subtype_definition ON subtype_definition.id = source.sub_type_id
SET field_definition.department_id = subtype_definition.department_id;

INSERT INTO sub_fields (sub_type_id, department_id, code, display_name, field_type, required_flag, options,
    share_config, sort_order, status, created_at, updated_at)
SELECT scoped_subtype.id, scoped_subtype.department_id, source.code, source.display_name, source.field_type,
    source.required_flag, source.options, source.share_config, source.sort_order, source.status,
    source.created_at, source.updated_at
FROM metadata_sub_field_source source
JOIN metadata_sub_type_source original_subtype ON original_subtype.id = source.sub_type_id
JOIN sub_types scoped_subtype
  ON scoped_subtype.master_type_id = original_subtype.master_type_id
  AND scoped_subtype.code = original_subtype.code
JOIN sub_types retained_subtype ON retained_subtype.id = source.sub_type_id
WHERE scoped_subtype.department_id <> retained_subtype.department_id;

UPDATE sub_records record
JOIN master_records master_record ON master_record.id = record.master_record_id
JOIN metadata_sub_type_source original_subtype ON original_subtype.id = record.sub_type_id
JOIN sub_types scoped_subtype
  ON scoped_subtype.master_type_id = original_subtype.master_type_id
  AND scoped_subtype.code = original_subtype.code
  AND scoped_subtype.department_id = master_record.department_id
SET record.sub_type_id = scoped_subtype.id;

UPDATE sub_record_drafts draft
JOIN master_record_drafts master_draft ON master_draft.id = draft.master_draft_id
JOIN metadata_sub_type_source original_subtype ON original_subtype.id = draft.sub_type_id
JOIN sub_types scoped_subtype
  ON scoped_subtype.master_type_id = original_subtype.master_type_id
  AND scoped_subtype.code = original_subtype.code
  AND scoped_subtype.department_id = master_draft.department_id
SET draft.sub_type_id = scoped_subtype.id;

ALTER TABLE master_fields
  MODIFY COLUMN department_id BIGINT NOT NULL,
  ADD UNIQUE KEY uk_master_fields_department_type_code (department_id, master_type_id, code),
  ADD CONSTRAINT fk_master_fields_department FOREIGN KEY (department_id) REFERENCES departments (id);

ALTER TABLE sub_types
  MODIFY COLUMN department_id BIGINT NOT NULL,
  ADD UNIQUE KEY uk_sub_types_department_master_code (department_id, master_type_id, code),
  ADD CONSTRAINT fk_sub_types_department FOREIGN KEY (department_id) REFERENCES departments (id);

ALTER TABLE sub_fields
  MODIFY COLUMN department_id BIGINT NOT NULL,
  ADD UNIQUE KEY uk_sub_fields_department_type_code (department_id, sub_type_id, code),
  ADD CONSTRAINT fk_sub_fields_department FOREIGN KEY (department_id) REFERENCES departments (id);

ALTER TABLE department_master_types
  ADD COLUMN active_department_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status = 'ACTIVE' THEN department_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_department_master_types_active (active_department_id);
