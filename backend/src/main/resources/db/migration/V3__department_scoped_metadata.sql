ALTER TABLE master_fields
  ADD COLUMN department_id BIGINT NOT NULL AFTER master_type_id,
  DROP INDEX uk_master_fields_type_code,
  ADD UNIQUE KEY uk_master_fields_department_type_code (department_id, master_type_id, code),
  ADD CONSTRAINT fk_master_fields_department FOREIGN KEY (department_id) REFERENCES departments (id);

ALTER TABLE sub_types
  ADD COLUMN department_id BIGINT NOT NULL AFTER master_type_id,
  DROP INDEX uk_sub_types_master_code,
  ADD UNIQUE KEY uk_sub_types_department_master_code (department_id, master_type_id, code),
  ADD CONSTRAINT fk_sub_types_department FOREIGN KEY (department_id) REFERENCES departments (id);

ALTER TABLE sub_fields
  ADD COLUMN department_id BIGINT NOT NULL AFTER sub_type_id,
  DROP INDEX uk_sub_fields_type_code,
  ADD UNIQUE KEY uk_sub_fields_department_type_code (department_id, sub_type_id, code),
  ADD CONSTRAINT fk_sub_fields_department FOREIGN KEY (department_id) REFERENCES departments (id);

ALTER TABLE department_master_types
  ADD COLUMN active_department_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status = 'ACTIVE' THEN department_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_department_master_types_active (active_department_id);
