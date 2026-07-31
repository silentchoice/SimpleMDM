package com.simplemdm.model.mdm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TypedValue(
    String stringValue,
    String textValue,
    Long integerValue,
    BigDecimal decimalValue,
    Boolean booleanValue,
    LocalDate dateValue,
    LocalDateTime datetimeValue,
    Long referenceRecordId
) {
    public TypedValue {
        int populated = 0;
        if (stringValue != null) populated++;
        if (textValue != null) populated++;
        if (integerValue != null) populated++;
        if (decimalValue != null) populated++;
        if (booleanValue != null) populated++;
        if (dateValue != null) populated++;
        if (datetimeValue != null) populated++;
        if (referenceRecordId != null) populated++;
        if (populated > 1) throw new IllegalArgumentException("TypedValue may contain at most one populated column");
    }

    public static TypedValue empty() {
        return new TypedValue(null, null, null, null, null, null, null, null);
    }

    public int nonNullValueCount() {
        int count = 0;
        if (stringValue != null) count++;
        if (textValue != null) count++;
        if (integerValue != null) count++;
        if (decimalValue != null) count++;
        if (booleanValue != null) count++;
        if (dateValue != null) count++;
        if (datetimeValue != null) count++;
        if (referenceRecordId != null) count++;
        return count;
    }
}
