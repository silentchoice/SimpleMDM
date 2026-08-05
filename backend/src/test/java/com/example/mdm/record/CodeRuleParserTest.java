package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mdm.common.error.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CodeRuleParserTest {
  private final CodeRuleParser parser = new CodeRuleParser();

  @Test void rendersDateAndZeroPaddedSequence() {
    assertThat(parser.parse("CUS-{yyyyMMdd}-{0001}").render(LocalDate.of(2026, 8, 5), 7))
        .isEqualTo("CUS-20260805-0007");
  }

  @Test void rejectsUnknownVariable() {
    assertThatThrownBy(() -> parser.parse("{department}-{0001}"))
        .isInstanceOf(BusinessException.class);
  }

  @Test void rejectsMultipleSequences() {
    assertThatThrownBy(() -> parser.parse("CUS-{0001}-{0002}"))
        .isInstanceOf(BusinessException.class);
  }
}
