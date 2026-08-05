package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mdm.common.error.BusinessException;
import java.util.ArrayList;
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

  @ParameterizedTest(name = "rejects invalid display name [{0}]")
  @MethodSource("invalidDisplayNames")
  void rejectsBlankOrOverlongDisplayNames(String displayName) {
    assertBadRequest("serial",
        () -> validator.validate(field("serial", displayName, FieldType.TEXT, List.of())));
  }

  static Stream<Arguments> invalidDisplayNames() {
    return Stream.of(
        Arguments.of((String) null),
        Arguments.of(""),
        Arguments.of(" "),
        Arguments.of("x".repeat(129)));
  }

  @Test
  void acceptsDisplayNameAtMaximumLength() {
    assertThatCode(() -> validator.validate(
        field("serial", "x".repeat(128), FieldType.TEXT, List.of())))
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

  @Test
  void rejectsNullSelectionOptionAsFieldSpecificBadRequest() {
    String code = "choice";
    List<String> options = new ArrayList<>();
    options.add("A");
    options.add(null);

    assertBadRequest(code,
        () -> validator.validate(field(code, FieldType.SELECT, options)));
  }

  @Test
  void rejectsNullFieldTypeAsFieldSpecificBadRequest() {
    String code = "value";

    assertBadRequest(code, () -> validator.validate(field(code, null, List.of())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"TEXT", "NUMBER", "DATE", "DATETIME", "SWITCH"})
  void rejectsOptionsOnNonSelectionFields(String type) {
    String code = "value";
    assertBadRequest(code,
        () -> validator.validate(field(code, FieldType.valueOf(type), List.of("A"))));
  }

  private FieldDefinition field(String code, FieldType type, List<String> options) {
    return field(code, code, type, options);
  }

  private FieldDefinition field(String code, String displayName, FieldType type,
      List<String> options) {
    return new FieldDefinition(1L, 2L, code, displayName, type, false, options, false, 0,
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
