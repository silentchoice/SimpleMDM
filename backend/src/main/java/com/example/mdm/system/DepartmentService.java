package com.example.mdm.system;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DepartmentService {
  private final DepartmentRepository repository;
  private final AuthorizationService authorization;

  public DepartmentService(DepartmentRepository repository, AuthorizationService authorization) {
    this.repository = repository;
    this.authorization = authorization;
  }

  public Department create(String code, String name) {
    authorization.requireRole(Role.SUPER_ADMIN);
    return repository.create(code.trim().toUpperCase(Locale.ROOT), name.trim());
  }

  public Department update(long id, String code, String name) {
    authorization.requireRole(Role.SUPER_ADMIN);
    return repository.update(id, code.trim().toUpperCase(Locale.ROOT), name.trim());
  }

  public void setStatus(long id, EntityStatus status) {
    authorization.requireRole(Role.SUPER_ADMIN);
    repository.setStatus(id, status);
  }

  public List<Department> list() {
    authorization.requireRole(Role.SUPER_ADMIN);
    return repository.findAll();
  }

  public Department get(long id) {
    authorization.requireRole(Role.SUPER_ADMIN);
    return repository.findById(id);
  }
}
