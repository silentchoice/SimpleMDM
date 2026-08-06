package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class EditLockServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
  private final RecordRepository records = Mockito.mock(RecordRepository.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final MemoryLockStore locks = new MemoryLockStore();
  private final UserPrincipal editor = principal(12L, "Editor");
  private EditLockService service;

  @BeforeEach void setUp() {
    service = new EditLockService(records, locks, authorization,
        Clock.fixed(NOW, ZoneOffset.UTC));
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor);
    when(records.findRecord(7L, 42L)).thenReturn(record(42L));
  }

  @Test void firstAcquisitionCreatesAThirtyMinuteLockForTheAuthenticatedEditor() {
    EditLock lock = service.acquire(42L);

    assertThat(lock.recordId()).isEqualTo(42L);
    assertThat(lock.departmentId()).isEqualTo(7L);
    assertThat(lock.userId()).isEqualTo(12L);
    assertThat(lock.displayName()).isEqualTo("Editor");
    assertThat(lock.token()).hasSize(43);
    assertThat(lock.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    assertThat(locks.ttlFor(42L)).isEqualTo(Duration.ofMinutes(30));
    verify(authorization).requireDepartment(7L);
  }

  @Test void acquiringAgainAsTheOwnerReturnsTheOriginalLockWithoutChangingItsExpiry() {
    EditLock first = service.acquire(42L);

    EditLock repeated = service.acquire(42L);

    assertThat(repeated).isEqualTo(first);
  }

  @Test void competingEditorReceivesConflictNamingTheCurrentHolderAndExpiry() {
    EditLock held = service.acquire(42L);
    UserPrincipal other = principal(13L, "Other editor");
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(other);

    assertThatThrownBy(() -> service.acquire(42L))
        .isInstanceOfSatisfying(EditLockConflictException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.getMessage()).contains("Editor", held.expiresAt().toString());
          assertThat(error.getMessage()).doesNotContain(held.token());
          assertThat(error.details().holderId()).isEqualTo(12L);
          assertThat(error.details().holderDisplayName()).isEqualTo("Editor");
          assertThat(error.details().expiresAt()).isEqualTo(held.expiresAt());
        });
  }

  @Test void renewalRequiresTheOwnerTokenAndResetsTheFullThirtyMinuteTtl() {
    EditLock held = service.acquire(42L);
    service = new EditLockService(records, locks, authorization,
        Clock.fixed(NOW.plus(Duration.ofMinutes(9)), ZoneOffset.UTC));

    EditLock renewed = service.renew(42L, held.token());

    assertThat(renewed.token()).isEqualTo(held.token());
    assertThat(renewed.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(39)));
    assertThat(locks.ttlFor(42L)).isEqualTo(Duration.ofMinutes(30));
    assertThatThrownBy(() -> service.renew(42L, "wrong-token"))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test void releaseCannotDeleteAnotherOwnersLockAndTheRightTokenReleasesIt() {
    EditLock held = service.acquire(42L);

    assertThatThrownBy(() -> service.release(42L, "wrong-token"))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(locks.find(42L)).isEqualTo(held);

    service.release(42L, held.token());

    assertThat(locks.find(42L)).isNull();
  }

  @Test void foreignDepartmentRecordCannotBeLocked() {
    when(records.findRecord(7L, 42L)).thenThrow(BusinessException.forbidden());

    assertThatThrownBy(() -> service.acquire(42L))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(locks.find(42L)).isNull();
  }

  @Test void anExpiredLockIsReplacedByANewOwner() {
    EditLock expired = new EditLock(42L, 7L, 12L, "Editor", "expired-token",
        NOW.minusSeconds(1));
    locks.put(expired, Duration.ofMinutes(30));
    UserPrincipal other = principal(13L, "Other editor");
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(other);

    EditLock replacement = service.acquire(42L);

    assertThat(replacement.userId()).isEqualTo(13L);
    assertThat(replacement.token()).isNotEqualTo(expired.token());
  }

  private static UserPrincipal principal(long id, String displayName) {
    return new UserPrincipal(id, "user" + id, displayName,
        new DepartmentPrincipal(7L, "D7", "Department 7"), List.of(Role.DEPT_EDITOR));
  }

  private static RecordView record(long id) {
    return new RecordView(id, 9L, 7L, "CUS-42", Map.of(), List.of(), 3L, "ACTIVE");
  }

  private static final class MemoryLockStore implements EditLockStore {
    private final Map<Long, EditLock> values = new HashMap<>();
    private final Map<Long, Duration> ttls = new HashMap<>();

    @Override public EditLock find(long recordId) {
      EditLock lock = values.get(recordId);
      if (lock != null && !lock.expiresAt().isAfter(NOW)) {
        values.remove(recordId);
        ttls.remove(recordId);
        return null;
      }
      return lock;
    }
    @Override public boolean acquire(EditLock lock, Duration ttl) {
      if (values.containsKey(lock.recordId())) return false;
      put(lock, ttl);
      return true;
    }
    @Override public boolean renew(long recordId, String token, EditLock replacement, Duration ttl) {
      EditLock current = values.get(recordId);
      if (current == null || !current.token().equals(token)) return false;
      put(replacement, ttl);
      return true;
    }
    @Override public boolean release(long recordId, String token) {
      EditLock current = values.get(recordId);
      if (current == null || !current.token().equals(token)) return false;
      values.remove(recordId);
      ttls.remove(recordId);
      return true;
    }
    void put(EditLock lock, Duration ttl) { values.put(lock.recordId(), lock); ttls.put(lock.recordId(), ttl); }
    Duration ttlFor(long recordId) { return ttls.get(recordId); }
  }
}
