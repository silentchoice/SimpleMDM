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
