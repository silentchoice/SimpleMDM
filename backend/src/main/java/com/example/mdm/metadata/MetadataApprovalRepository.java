package com.example.mdm.metadata;

public interface MetadataApprovalRepository {
  long submit(MetadataChangeRequest request);

  long requireSubTypeTemplate(long departmentId, long subTypeId);
}
