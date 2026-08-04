package com.example.mdm.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record FieldDefinition(long id, long ownerTypeId, String code, String displayName,
    FieldType fieldType, boolean required, List<String> options, boolean shared, int sortOrder,
    MetadataStatus status) {
  public FieldDefinition {
    options = options == null ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(options));
  }

  public boolean supportsOptions() {
    return fieldType == FieldType.SELECT || fieldType == FieldType.RADIO
        || fieldType == FieldType.MULTISELECT;
  }
}
