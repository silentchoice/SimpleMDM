package com.example.mdm.metadata;

import com.example.mdm.common.error.BusinessException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FieldStructureValidator {
  private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

  public void validate(FieldDefinition definition) {
    String code = definition.code();
    if (code == null || !CODE_PATTERN.matcher(code).matches()) {
      throw invalid(code, "invalid code");
    }
    if (definition.fieldType() == null) {
      throw invalid(code, "field type is required");
    }

    if (!definition.supportsOptions()) {
      if (!definition.options().isEmpty()) {
        throw invalid(code, "options are not supported");
      }
      return;
    }

    if (definition.options().isEmpty()) {
      throw invalid(code, "selection options are required");
    }
    Set<String> unique = new HashSet<>();
    for (String option : definition.options()) {
      if (option == null || option.isBlank()) {
        throw invalid(code, "selection options must not be blank");
      }
      if (!unique.add(option)) {
        throw invalid(code, "selection options must be unique");
      }
    }
  }

  private BusinessException invalid(String code, String reason) {
    return BusinessException.badRequest("Invalid field " + String.valueOf(code) + ": " + reason);
  }
}
