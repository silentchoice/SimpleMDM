package com.example.mdm.system;

import com.example.mdm.auth.Role;
import java.util.List;

public record SystemUser(long id, String username, String displayName, Long departmentId,
                         EntityStatus status, List<Role> roles) {
  public SystemUser { roles = List.copyOf(roles); }
}
