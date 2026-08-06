package com.example.mdm.record;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EditLockService {
  private static final Duration TTL = Duration.ofMinutes(30);
  private final RecordRepository records;
  private final EditLockStore locks;
  private final AuthorizationService authorization;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public EditLockService(RecordRepository records, EditLockStore locks,
      AuthorizationService authorization, Clock clock) {
    this.records = records;
    this.locks = locks;
    this.authorization = authorization;
    this.clock = clock;
  }

  @Autowired
  EditLockService(RecordRepository records, EditLockStore locks,
      AuthorizationService authorization, ObjectProvider<Clock> clocks) {
    this(records, locks, authorization, clocks.getIfAvailable(Clock::systemUTC));
  }

  public EditLock acquire(long recordId) {
    UserPrincipal actor = editor();
    records.findRecord(actor.department().id(), recordId);
    EditLock current = locks.find(recordId);
    if (current != null) return ownedOrConflict(current, actor);
    EditLock created = newLock(recordId, actor, clock.instant());
    if (locks.acquire(created, TTL)) return created;
    current = locks.find(recordId);
    if (current != null) return ownedOrConflict(current, actor);
    if (locks.acquire(created, TTL)) return created;
    current = locks.find(recordId);
    if (current != null) return ownedOrConflict(current, actor);
    throw new BusinessException(HttpStatus.CONFLICT, "Record lock is changing; retry");
  }

  public EditLock renew(long recordId, String token) {
    UserPrincipal actor = editor();
    records.findRecord(actor.department().id(), recordId);
    EditLock current = locks.find(recordId);
    requireOwned(current, actor, token);
    EditLock renewed = new EditLock(recordId, actor.department().id(), actor.id(),
        actor.displayName(), token, clock.instant().plus(TTL));
    if (!locks.renew(recordId, token, renewed, TTL)) throw missingLock();
    return renewed;
  }

  public void release(long recordId, String token) {
    UserPrincipal actor = editor();
    records.findRecord(actor.department().id(), recordId);
    EditLock current = locks.find(recordId);
    requireOwned(current, actor, token);
    if (!locks.release(recordId, token)) throw missingLock();
  }

  private EditLock ownedOrConflict(EditLock current, UserPrincipal actor) {
    if (current.userId() == actor.id() && current.departmentId() == actor.department().id()) {
      return current;
    }
    throw new EditLockConflictException(current);
  }

  private void requireOwned(EditLock current, UserPrincipal actor, String token) {
    if (current == null || token == null || !current.token().equals(token)) throw missingLock();
    if (current.userId() != actor.id() || current.departmentId() != actor.department().id()) {
      throw BusinessException.forbidden();
    }
  }

  private EditLock newLock(long recordId, UserPrincipal actor, Instant now) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new EditLock(recordId, actor.department().id(), actor.id(), actor.displayName(), token,
        now.plus(TTL));
  }

  private UserPrincipal editor() {
    UserPrincipal actor = authorization.requireRole(Role.DEPT_EDITOR);
    if (actor.department() == null) throw BusinessException.forbidden();
    authorization.requireDepartment(actor.department().id());
    return actor;
  }

  private BusinessException missingLock() {
    return new BusinessException(HttpStatus.CONFLICT, "Edit lock is no longer held");
  }
}
