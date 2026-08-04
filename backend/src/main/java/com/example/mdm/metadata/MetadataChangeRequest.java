package com.example.mdm.metadata;

import java.util.Objects;

public record MetadataChangeRequest(long departmentId, long submittedBy, String entityKind,
    long entityId, String beforeSnapshot, String afterSnapshot) {
  public MetadataChangeRequest {
    Objects.requireNonNull(entityKind, "entityKind");
    Objects.requireNonNull(beforeSnapshot, "beforeSnapshot");
    Objects.requireNonNull(afterSnapshot, "afterSnapshot");
  }
}
