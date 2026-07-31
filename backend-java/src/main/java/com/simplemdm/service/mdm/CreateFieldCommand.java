package com.simplemdm.service.mdm;

import com.simplemdm.model.mdm.FieldDataType;

public record CreateFieldCommand(
    String fieldKey,
    String fieldName,
    FieldDataType dataType,
    boolean required,
    boolean uniqueValue,
    boolean searchable,
    boolean shared,
    Integer maxLength,
    Integer precision,
    Integer scale,
    Long referenceObjectTypeId,
    String defaultValue,
    String validationRule,
    int sortOrder
) { }
