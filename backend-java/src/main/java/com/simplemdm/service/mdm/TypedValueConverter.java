package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.TypedValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class TypedValueConverter {

    public TypedValue convert(FieldDefinition field, Object rawValue) {
        if (isBlank(rawValue)) {
            if (field.isRequired()) throw badRequest("Field is required");
            return TypedValue.empty();
        }
        try {
            return switch (field.getDataType()) {
                case STRING -> new TypedValue(requireString(rawValue, field), null, null, null, null, null, null, null);
                case TEXT -> new TypedValue(null, requireString(rawValue, field), null, null, null, null, null, null);
                case INTEGER -> new TypedValue(null, null, toLong(rawValue), null, null, null, null, null);
                case DECIMAL -> new TypedValue(null, null, null, toDecimal(rawValue, field), null, null, null, null);
                case BOOLEAN -> new TypedValue(null, null, null, null, toBoolean(rawValue), null, null, null);
                case DATE -> new TypedValue(null, null, null, null, null, toDate(rawValue), null, null);
                case DATETIME -> new TypedValue(null, null, null, null, null, null, toDateTime(rawValue), null);
                case REFERENCE -> new TypedValue(null, null, null, null, null, null, null, toReference(rawValue, field));
            };
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw badRequest("Value does not match field data type");
        }
    }

    private String requireString(Object rawValue, FieldDefinition field) {
        if (!(rawValue instanceof String value)) throw badRequest("Value does not match field data type");
        if (field.getMaxLength() != null && value.length() > field.getMaxLength()) throw badRequest("Value exceeds max length");
        return value;
    }

    private Long toLong(Object rawValue) {
        if (rawValue instanceof Long value) return value;
        if (rawValue instanceof Integer value) return value.longValue();
        if (rawValue instanceof String value) return Long.valueOf(value);
        throw badRequest("Value does not match field data type");
    }

    private BigDecimal toDecimal(Object rawValue, FieldDefinition field) {
        BigDecimal value = rawValue instanceof BigDecimal decimal ? decimal : new BigDecimal(rawValue.toString());
        if (field.getPrecision() != null && value.precision() > field.getPrecision()) throw badRequest("Decimal precision exceeds field precision");
        if (field.getScale() != null && value.scale() > field.getScale()) throw badRequest("Decimal scale exceeds field scale");
        return value;
    }

    private Boolean toBoolean(Object rawValue) {
        if (rawValue instanceof Boolean value) return value;
        if (rawValue instanceof String value && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            return Boolean.valueOf(value.toLowerCase(Locale.ROOT));
        }
        throw badRequest("Value does not match field data type");
    }

    private LocalDate toDate(Object rawValue) {
        if (rawValue instanceof LocalDate value) return value;
        if (rawValue instanceof String value) return LocalDate.parse(value);
        throw badRequest("Value does not match field data type");
    }

    private LocalDateTime toDateTime(Object rawValue) {
        if (rawValue instanceof LocalDateTime value) return value;
        if (rawValue instanceof String value) return LocalDateTime.parse(value);
        throw badRequest("Value does not match field data type");
    }

    private Long toReference(Object rawValue, FieldDefinition field) {
        if (!(rawValue instanceof ReferenceValue value)) throw badRequest("Value does not match field data type");
        if (!value.objectTypeId().equals(field.getReferenceObjectTypeId())) throw badRequest("Reference object type does not match field");
        if (!value.systemId().equals(field.getSystemId())) throw badRequest("Reference system does not match field");
        return value.recordId();
    }

    private boolean isBlank(Object value) { return value == null || value instanceof String text && text.isBlank(); }
    private BusinessException badRequest(String message) { return new BusinessException(400, message); }

    public record ReferenceValue(Long recordId, Long objectTypeId, Long systemId) { }
}
