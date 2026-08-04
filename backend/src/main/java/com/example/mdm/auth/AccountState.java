package com.example.mdm.auth;

import java.util.List;

public record AccountState(long userId, Long departmentId, List<Role> roles) {
  public AccountState { roles = List.copyOf(roles); }
}
