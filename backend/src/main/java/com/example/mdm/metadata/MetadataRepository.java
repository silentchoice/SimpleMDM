package com.example.mdm.metadata;

import java.util.List;

public interface MetadataRepository {
  MasterType createMasterType(String code, String name, long actorId);
  void assignDepartment(long departmentId, long masterTypeId);
  void requireAssignment(long departmentId, long masterTypeId);
  FieldDefinition createMasterField(long departmentId, FieldDefinition field);
  SubType createSubType(long departmentId, long masterTypeId, String code, String name);
  FieldDefinition createSubField(long departmentId, FieldDefinition field);
  List<MasterType> findMasterTypes();
  List<FieldDefinition> findMasterFields(long departmentId, long masterTypeId);
  List<SubType> findSubTypes(long departmentId, long masterTypeId);
  List<FieldDefinition> findSubFields(long departmentId, long subTypeId);

  default List<FieldDefinition> findMasterFields(long masterTypeId) {
    throw new UnsupportedOperationException("Department id is required");
  }

  default List<SubType> findSubTypes(long masterTypeId) {
    throw new UnsupportedOperationException("Department id is required");
  }

  default List<FieldDefinition> findSubFields(long subTypeId) {
    throw new UnsupportedOperationException("Department id is required");
  }

  default FieldDefinition createMasterField(FieldDefinition field) {
    throw new UnsupportedOperationException("Department id is required");
  }

  default SubType createSubType(long masterTypeId, String code, String name) {
    throw new UnsupportedOperationException("Department id is required");
  }

  default FieldDefinition createSubField(FieldDefinition field) {
    throw new UnsupportedOperationException("Department id is required");
  }
}
