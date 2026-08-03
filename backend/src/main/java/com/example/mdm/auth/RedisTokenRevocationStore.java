package com.example.mdm.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisTokenRevocationStore implements TokenRevocationStore {
  private static final String KEY_PREFIX = "mdm:auth:revoked:";
  private final StringRedisTemplate redisTemplate;

  RedisTokenRevocationStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void revoke(String jti, Duration ttl) {
    redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
  }

  @Override
  public boolean isRevoked(String jti) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
  }
}
