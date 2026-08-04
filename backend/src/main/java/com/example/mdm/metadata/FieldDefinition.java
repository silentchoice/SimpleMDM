package com.example.mdm.metadata;

import java.util.List;

public record FieldDefinition(long id, long ownerTypeId, String code, String displayName,
    FieldType fieldType, boolean required, List<String> options, boolean shared, int sortOrder,
    MetadataStatus status) {
  public FieldDefinition { options = options == null ? List.of() : List.copyOf(options); }
}
