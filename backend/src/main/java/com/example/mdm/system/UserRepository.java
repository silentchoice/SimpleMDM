package com.example.mdm.system;

import com.example.mdm.auth.Role;
import java.util.List;

public interface UserRepository {
  SystemUser create(String username, String passwordHash, String displayName, Long departmentId, List<Role> roles);
  SystemUser update(long id, String displayName, Long departmentId);
  void setStatus(long id, EntityStatus status);
  void assignRoles(long id, List<Role> roles);
  List<SystemUser> findAll();
  boolean exists(long id);
  boolean isDepartmentActive(long departmentId);
  void createInitialAdministrator(String username, String passwordHash, String displayName);
}
