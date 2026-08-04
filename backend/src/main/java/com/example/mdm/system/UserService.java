package com.example.mdm.system;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import java.util.List;
import com.example.mdm.common.error.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository repository;
  private final AuthorizationService authorization;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository repository, AuthorizationService authorization,
      PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.authorization = authorization;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public SystemUser create(String username, String password, String displayName, Long departmentId,
      List<Role> roles) {
    authorization.requireRole(Role.SUPER_ADMIN);
    if (roles == null) throw BusinessException.badRequest("Roles are required");
    requireActiveDepartment(departmentId);
    return repository.create(username.trim(), passwordEncoder.encode(password), displayName.trim(), departmentId, roles);
  }

  @Transactional
  public SystemUser update(long id, String displayName, Long departmentId) {
    authorization.requireRole(Role.SUPER_ADMIN);
    requireActiveDepartment(departmentId);
    return repository.update(id, displayName.trim(), departmentId);
  }

  public void setStatus(long id, EntityStatus status) {
    authorization.requireRole(Role.SUPER_ADMIN);
    repository.setStatus(id, status);
  }

  public void assignRoles(long id, List<Role> roles) {
    authorization.requireRole(Role.SUPER_ADMIN);
    if (roles == null) throw BusinessException.badRequest("Roles are required");
    if (!repository.exists(id)) throw BusinessException.notFound("User");
    repository.assignRoles(id, roles);
  }

  private void requireActiveDepartment(Long departmentId) {
    if (departmentId != null && !repository.isDepartmentActive(departmentId)) {
      throw BusinessException.notFound("Department");
    }
  }

  public List<SystemUser> list() {
    authorization.requireRole(Role.SUPER_ADMIN);
    return repository.findAll();
  }
}
