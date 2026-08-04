ALTER TABLE sub_types
  ADD COLUMN sort_order INT NULL AFTER name;

UPDATE sub_types subtype_definition
JOIN (
  SELECT id,
    ROW_NUMBER() OVER (PARTITION BY department_id, master_type_id ORDER BY id) - 1 AS position
  FROM sub_types
) ranked ON ranked.id = subtype_definition.id
SET subtype_definition.sort_order = ranked.position;

ALTER TABLE sub_types
  MODIFY COLUMN sort_order INT NOT NULL DEFAULT 0,
  ADD INDEX idx_sub_types_department_template_order
    (department_id, master_type_id, sort_order, id);
