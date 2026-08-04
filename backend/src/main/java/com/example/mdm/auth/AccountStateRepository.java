package com.example.mdm.auth;

public interface AccountStateRepository {
  AccountState findActive(long userId);
}
