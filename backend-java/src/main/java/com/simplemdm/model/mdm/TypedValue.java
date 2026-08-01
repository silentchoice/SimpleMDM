package com.simplemdm.model.mdm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

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

    public boolean sameValueAs(TypedValue other) {
        if (other == null) return false;
        return Objects.equals(stringValue, other.stringValue)
            && Objects.equals(textValue, other.textValue)
            && Objects.equals(integerValue, other.integerValue)
            && sameDecimal(decimalValue, other.decimalValue)
            && Objects.equals(booleanValue, other.booleanValue)
            && Objects.equals(dateValue, other.dateValue)
            && Objects.equals(datetimeValue, other.datetimeValue)
            && Objects.equals(referenceRecordId, other.referenceRecordId);
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }
}
