package com.example.mdm.record;

import java.time.Duration;

public interface EditLockStore {
  EditLock find(long recordId);
  boolean acquire(EditLock lock, Duration ttl);
  boolean renew(long recordId, String token, EditLock replacement, Duration ttl);
  boolean release(long recordId, String token);
}
