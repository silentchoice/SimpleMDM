package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mdm.common.error.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class FieldValueValidatorTest {
  private final FieldValueValidator validator = new FieldValueValidator();

  @Test
  void rejectsUnknownInputKeys() {
    assertBadRequest("unknown", () -> validator.validate(
        List.of(field("name", FieldType.TEXT, false, List.of())),
        Map.of("unknown", "value")));
  }

  @Test
  void rejectsDuplicateActiveDefinitionCodes() {
    FieldDefinition first = field("name", FieldType.TEXT, false, List.of());
    FieldDefinition duplicate = new FieldDefinition(2L, 2L, "name", "duplicate",
        FieldType.TEXT, false, List.of(), false, 1, MetadataStatus.ACTIVE);

    assertBadRequest("name",
        () -> validator.validate(List.of(first, duplicate), Map.of("name", "value")));
  }

  @Test
  void rejectsAbsentRequiredKey() {
    assertBadRequest("name", () -> validator.validate(
        List.of(field("name", FieldType.TEXT, true, List.of())), Map.of()));
  }

  @ParameterizedTest(name = "rejects missing required value [{0}]")
  @MethodSource("emptyRequiredValues")
  void rejectsEmptyRequiredValues(Object value) {
    Map<String, Object> values = new HashMap<>();
    values.put("name", value);

    assertBadRequest("name", () -> validator.validate(
        List.of(field("name", FieldType.TEXT, true, List.of())), values));
  }

  static Stream<Arguments> emptyRequiredValues() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(""),
        Arguments.of("   "),
        Arguments.of(new ArrayList<>()));
  }

  @ParameterizedTest(name = "accepts {0}")
  @MethodSource("validValues")
  void acceptsValuesMatchingTheirFieldTypes(FieldType type, Object value, List<String> options) {
    assertThatCode(() -> validator.validate(
        List.of(field("value", type, true, options)), Map.of("value", value)))
        .doesNotThrowAnyException();
  }

  static Stream<Arguments> validValues() {
    return Stream.of(
        Arguments.of(FieldType.TEXT, "unchanged text", List.of()),
        Arguments.of(FieldType.NUMBER, new BigDecimal("12.50"), List.of()),
        Arguments.of(FieldType.DATE, "2026-08-04", List.of()),
        Arguments.of(FieldType.DATETIME, "2026-08-04T13:45:00+08:00", List.of()),
        Arguments.of(FieldType.DATETIME, "2026-08-04T13:45:00", List.of()),
        Arguments.of(FieldType.SWITCH, true, List.of()),
        Arguments.of(FieldType.SELECT, "A", List.of("A", "B")),
        Arguments.of(FieldType.RADIO, "B", List.of("A", "B")),
        Arguments.of(FieldType.MULTISELECT, List.of("A", "B"), List.of("A", "B")));
  }

  @ParameterizedTest(name = "rejects invalid {0} value [{1}]")
  @MethodSource("invalidValues")
  void rejectsValuesNotMatchingTheirFieldTypes(FieldType type, Object value,
      List<String> options) {
    assertBadRequest("value", () -> validator.validate(
        List.of(field("value", type, false, options)), Map.of("value", value)));
  }

  static Stream<Arguments> invalidValues() {
    return Stream.of(
        Arguments.of(FieldType.TEXT, 10, List.of()),
        Arguments.of(FieldType.NUMBER, "12.50", List.of()),
        Arguments.of(FieldType.DATE, "2026-02-30", List.of()),
        Arguments.of(FieldType.DATE, "2026-08-04 ", List.of()),
        Arguments.of(FieldType.DATETIME, "2026-08-04", List.of()),
        Arguments.of(FieldType.SWITCH, "true", List.of()),
        Arguments.of(FieldType.SELECT, " A ", List.of("A", "B")),
        Arguments.of(FieldType.RADIO, "C", List.of("A", "B")),
        Arguments.of(FieldType.MULTISELECT, "A", List.of("A", "B")),
        Arguments.of(FieldType.MULTISELECT, List.of("A", "C"), List.of("A", "B")),
        Arguments.of(FieldType.MULTISELECT, List.of("A", 2), List.of("A", "B")));
  }

  private FieldDefinition field(String code, FieldType type, boolean required, List<String> options) {
    return new FieldDefinition(1L, 2L, code, code, type, required, options, false, 0,
        MetadataStatus.ACTIVE);
  }

  private void assertBadRequest(String fieldCode, ThrowingCall call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(exception.getMessage()).contains(fieldCode);
        });
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run();
  }
}
