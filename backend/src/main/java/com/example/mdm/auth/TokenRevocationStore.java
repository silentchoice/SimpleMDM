package com.example.mdm.auth;

import java.time.Duration;

public interface TokenRevocationStore {
  void revoke(String jti, Duration ttl);

  boolean isRevoked(String jti);
}
