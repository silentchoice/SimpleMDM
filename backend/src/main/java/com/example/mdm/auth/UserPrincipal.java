package com.example.mdm.auth;

import java.util.List;

public record UserPrincipal(long id, String username, String displayName, DepartmentPrincipal department,
                            List<Role> roles) {
  public UserPrincipal {
    roles = List.copyOf(roles);
  }
}
