package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisEditLockStoreTest {
  @Test void compareAndRenewOnlyExtendsTheMatchingTokenAndKeepsTheNewSerializedExpiry() {
    AtomicRedis redis = new AtomicRedis();
    RedisEditLockStore store = new RedisEditLockStore(redis.template());
    EditLock original = lock("first-token", "2026-08-05T08:30:00Z");
    EditLock replacement = lock("first-token", "2026-08-05T08:45:00Z");
    assertThat(store.acquire(original, Duration.ofMinutes(30))).isTrue();

    assertThat(store.renew(42L, "wrong-token", replacement, Duration.ofMinutes(30))).isFalse();
    assertThat(store.find(42L)).isEqualTo(original);
    assertThat(store.renew(42L, "first-token", replacement, Duration.ofMinutes(30))).isTrue();

    assertThat(store.find(42L)).isEqualTo(replacement);
    assertThat(redis.ttlFor("mdm:record:edit-lock:42")).isEqualTo(Duration.ofMinutes(30));
  }

  @Test void compareAndDeleteCannotRemoveALockWhoseTokenChangedBetweenRequests() {
    AtomicRedis redis = new AtomicRedis();
    RedisEditLockStore store = new RedisEditLockStore(redis.template());
    EditLock original = lock("first-token", "2026-08-05T08:30:00Z");
    EditLock replacement = lock("second-token", "2026-08-05T08:45:00Z");
    assertThat(store.acquire(original, Duration.ofMinutes(30))).isTrue();
    assertThat(store.renew(42L, "first-token", replacement, Duration.ofMinutes(30))).isTrue();

    assertThat(store.release(42L, "first-token")).isFalse();
    assertThat(store.find(42L)).isEqualTo(replacement);
    assertThat(store.release(42L, "second-token")).isTrue();
    assertThat(store.find(42L)).isNull();
  }

  private static EditLock lock(String token, String expiry) {
    return new EditLock(42L, 7L, 12L, "Editor | principal", token, Instant.parse(expiry));
  }

  private static final class AtomicRedis {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Duration> ttls = new HashMap<>();
    private final StringRedisTemplate template = org.mockito.Mockito.mock(StringRedisTemplate.class);

    AtomicRedis() {
      var valuesOps = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
      when(template.opsForValue()).thenReturn(valuesOps);
      when(valuesOps.get(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
      when(valuesOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
        String key = call.getArgument(0);
        if (values.containsKey(key)) return false;
        values.put(key, call.getArgument(1));
        ttls.put(key, call.getArgument(2));
        return true;
      });
      when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
          .thenAnswer(this::executeAtomically);
    }

    StringRedisTemplate template() { return template; }
    Duration ttlFor(String key) { return ttls.get(key); }

    private Long executeAtomically(InvocationOnMock call) {
      @SuppressWarnings("unchecked") List<String> keys = call.getArgument(1);
      Object[] args = new Object[call.getArguments().length - 2];
      System.arraycopy(call.getArguments(), 2, args, 0, args.length);
      String key = keys.get(0);
      String value = values.get(key);
      if (value == null || !token(value).equals(args[0])) return 0L;
      if (args.length == 1) {
        values.remove(key);
        ttls.remove(key);
      } else {
        values.put(key, (String) args[1]);
        ttls.put(key, Duration.ofMillis(Long.parseLong((String) args[2])));
      }
      return 1L;
    }

    private String token(String value) { return value.split("\\|", -1)[3]; }
  }
}
