package com.example.mdm.metadata;

import com.example.mdm.common.error.BusinessException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FieldValueValidator {
  public void validate(List<FieldDefinition> definitions, Map<String, Object> values) {
    Map<String, FieldDefinition> fields = definitions.stream()
        .filter(field -> field.status() == MetadataStatus.ACTIVE)
        .collect(Collectors.toMap(FieldDefinition::code, Function.identity()));
    for (String code : values.keySet()) {
      if (!fields.containsKey(code)) throw BusinessException.badRequest("Unknown field: " + code);
    }
    for (FieldDefinition field : fields.values()) {
      Object value = values.get(field.code());
      if (field.required() && empty(value)) {
        throw BusinessException.badRequest("Required field is missing: " + field.code());
      }
      if (value != null) validateType(field, value);
    }
  }

  private void validateType(FieldDefinition field, Object value) {
    boolean valid = switch (field.fieldType()) {
      case TEXT -> value instanceof String;
      case NUMBER -> value instanceof Number;
      case DATE -> value instanceof String text && parsesDate(text);
      case DATETIME -> value instanceof String text && parsesDateTime(text);
      case SWITCH -> value instanceof Boolean;
      case SELECT, RADIO -> value instanceof String text && field.options().contains(text);
      case MULTISELECT -> value instanceof Collection<?> items
          && items.stream().allMatch(item -> item instanceof String text && field.options().contains(text));
    };
    if (!valid) {
      if (field.supportsOptions()) {
        throw BusinessException.badRequest("Invalid option for field: " + field.code());
      }
      throw BusinessException.badRequest("Invalid value for field: " + field.code());
    }
  }

  private boolean empty(Object value) {
    return value == null || value instanceof String text && text.isBlank()
        || value instanceof Collection<?> items && items.isEmpty();
  }
  private boolean parsesDate(String value) { try { LocalDate.parse(value); return true; } catch (RuntimeException e) { return false; } }
  private boolean parsesDateTime(String value) {
    try { OffsetDateTime.parse(value); return true; } catch (RuntimeException ignored) {
      try { LocalDateTime.parse(value); return true; } catch (RuntimeException e) { return false; }
    }
  }
}
