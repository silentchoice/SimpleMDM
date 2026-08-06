package com.example.mdm.record;

import com.example.mdm.common.error.BusinessException;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public final class EditLockConflictException extends BusinessException {
  private final Details details;

  public EditLockConflictException(EditLock lock) {
    super(HttpStatus.CONFLICT, "Record is being edited by " + lock.displayName() + " until "
        + lock.expiresAt());
    details = new Details(lock.userId(), lock.displayName(), lock.expiresAt());
  }

  public Details details() {
    return details;
  }

  public record Details(long holderId, String holderDisplayName, Instant expiresAt) {}
}
