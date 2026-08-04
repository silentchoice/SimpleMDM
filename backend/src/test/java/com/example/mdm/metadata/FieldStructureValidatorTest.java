package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mdm.common.error.BusinessException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class FieldStructureValidatorTest {
  private final FieldStructureValidator validator = new FieldStructureValidator();

  @ParameterizedTest(name = "rejects invalid field code [{0}]")
  @MethodSource("invalidCodes")
  void rejectsInvalidFieldCodes(String code) {
    assertBadRequest(code, () -> validator.validate(field(code, FieldType.TEXT, List.of())));
  }

  static Stream<Arguments> invalidCodes() {
    return Stream.of(
        Arguments.of((String) null),
        Arguments.of(""),
        Arguments.of("1name"),
        Arguments.of("has-dash"),
        Arguments.of("has space"),
        Arguments.of("a".repeat(65)));
  }

  @Test
  void acceptsFieldCodeAtMaximumLength() {
    String code = "a" + "1_".repeat(31) + "z";

    assertThat(code).hasSize(64);
    assertThatCode(() -> validator.validate(field(code, FieldType.TEXT, List.of())))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SELECT", "RADIO", "MULTISELECT"})
  void rejectsSelectionFieldsWithoutOptions(String type) {
    String code = "choice";
    assertBadRequest(code,
        () -> validator.validate(field(code, FieldType.valueOf(type), List.of())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SELECT", "RADIO", "MULTISELECT"})
  void rejectsBlankSelectionOptions(String type) {
    String code = "choice";
    assertBadRequest(code,
        () -> validator.validate(field(code, FieldType.valueOf(type), List.of("A", " "))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SELECT", "RADIO", "MULTISELECT"})
  void rejectsDuplicateSelectionOptions(String type) {
    String code = "choice";
    assertBadRequest(code,
        () -> validator.validate(field(code, FieldType.valueOf(type), List.of("A", "A"))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"TEXT", "NUMBER", "DATE", "DATETIME", "SWITCH"})
  void rejectsOptionsOnNonSelectionFields(String type) {
    String code = "value";
    assertBadRequest(code,
        () -> validator.validate(field(code, FieldType.valueOf(type), List.of("A"))));
  }

  private FieldDefinition field(String code, FieldType type, List<String> options) {
    return new FieldDefinition(1L, 2L, code, code, type, false, options, false, 0,
        MetadataStatus.ACTIVE);
  }

  private void assertBadRequest(String fieldCode, ThrowingCall call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(exception.getMessage()).contains(String.valueOf(fieldCode));
        });
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run();
  }
}
