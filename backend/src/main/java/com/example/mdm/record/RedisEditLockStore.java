package com.example.mdm.record;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisEditLockStore implements EditLockStore {
  private static final String KEY_PREFIX = "mdm:record:edit-lock:";
  private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
      local value = redis.call('GET', KEYS[1])
      if not value then return 0 end
      local token = string.match(value, '^[^|]*|[^|]*|[^|]*|([^|]*)|')
      if token ~= ARGV[1] then return 0 end
      redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
      return 1
      """, Long.class);
  private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
      local value = redis.call('GET', KEYS[1])
      if not value then return 0 end
      local token = string.match(value, '^[^|]*|[^|]*|[^|]*|([^|]*)|')
      if token ~= ARGV[1] then return 0 end
      redis.call('DEL', KEYS[1])
      return 1
      """, Long.class);

  private final StringRedisTemplate redis;

  public RedisEditLockStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override public EditLock find(long recordId) {
    String value = redis.opsForValue().get(key(recordId));
    return value == null ? null : deserialize(recordId, value);
  }

  @Override public boolean acquire(EditLock lock, Duration ttl) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(lock.recordId()), serialize(lock), ttl));
  }

  @Override public boolean renew(long recordId, String token, EditLock replacement, Duration ttl) {
    Long result = redis.execute(RENEW, List.of(key(recordId)), token, serialize(replacement),
        Long.toString(ttl.toMillis()));
    return Long.valueOf(1L).equals(result);
  }

  @Override public boolean release(long recordId, String token) {
    Long result = redis.execute(RELEASE, List.of(key(recordId)), token);
    return Long.valueOf(1L).equals(result);
  }

  private String key(long recordId) {
    return KEY_PREFIX + recordId;
  }

  private String serialize(EditLock lock) {
    return lock.userId() + "|" + lock.departmentId() + "|" + encode(lock.displayName()) + "|"
        + lock.token() + "|" + lock.expiresAt().toEpochMilli();
  }

  private EditLock deserialize(long recordId, String value) {
    String[] fields = value.split("\\|", -1);
    if (fields.length != 5) throw new IllegalStateException("Invalid edit lock value");
    try {
      return new EditLock(recordId, Long.parseLong(fields[1]), Long.parseLong(fields[0]),
          decode(fields[2]), fields[3], Instant.ofEpochMilli(Long.parseLong(fields[4])));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Invalid edit lock value", exception);
    }
  }

  private String encode(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }
}
