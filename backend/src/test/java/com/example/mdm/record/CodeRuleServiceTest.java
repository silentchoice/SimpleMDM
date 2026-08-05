package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mdm.auth.AuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeRuleServiceTest {
  @Test void allocatesIndependentDailySequencesAndDoesNotReuseGaps() {
    var repository = new InMemoryCodeSequenceRepository();
    var service = new CodeRuleService(repository, new CodeRuleParser(),
        org.mockito.Mockito.mock(AuthorizationService.class),
        Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
    repository.save(new CodeRule(41, "CUS-{yyyyMMdd}-{0001}", 4));

    assertThat(service.allocate(41, LocalDate.of(2026, 8, 5))).isEqualTo("CUS-20260805-0001");
    assertThat(service.allocate(41, LocalDate.of(2026, 8, 5))).isEqualTo("CUS-20260805-0002");
    assertThat(service.allocate(41, LocalDate.of(2026, 8, 6))).isEqualTo("CUS-20260806-0001");
    assertThat(service.allocate(41, LocalDate.of(2026, 8, 5))).isEqualTo("CUS-20260805-0003");
  }

  static final class InMemoryCodeSequenceRepository implements CodeSequenceRepository {
    private final Map<Long, CodeRule> rules = new HashMap<>();
    private final Map<String, Integer> nextByDay = new HashMap<>();
    @Override public CodeRule findRule(long masterTypeId) { return rules.get(masterTypeId); }
    @Override public void save(CodeRule rule) { rules.put(rule.masterTypeId(), rule); }
    @Override public int allocate(long masterTypeId, LocalDate sequenceDate) {
      String key = masterTypeId + ":" + sequenceDate;
      int next = nextByDay.getOrDefault(key, 1);
      nextByDay.put(key, next + 1);
      return next;
    }
  }
}
