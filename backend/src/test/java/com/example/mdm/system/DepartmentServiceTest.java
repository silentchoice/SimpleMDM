package com.example.mdm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import org.junit.jupiter.api.Test;

class DepartmentServiceTest {
  @Test
  void creatingDepartmentRequiresSuperAdminAndPersistsNormalizedValues() {
    var authorization = org.mockito.Mockito.mock(AuthorizationService.class);
    var repository = org.mockito.Mockito.mock(DepartmentRepository.class);
    var service = new DepartmentService(repository, authorization);
    var saved = new Department(9L, "SALES", "Sales", EntityStatus.ACTIVE);
    org.mockito.Mockito.when(repository.create("SALES", "Sales")).thenReturn(saved);

    assertThat(service.create(" sales ", " Sales ")).isEqualTo(saved);

    verify(authorization).requireRole(Role.SUPER_ADMIN);
    verify(repository).create("SALES", "Sales");
  }

  @Test
  void readingDepartmentByIdRequiresSuperAdmin() {
    var authorization = org.mockito.Mockito.mock(AuthorizationService.class);
    var repository = org.mockito.Mockito.mock(DepartmentRepository.class);
    var service = new DepartmentService(repository, authorization);
    var department = new Department(9L, "SALES", "Sales", EntityStatus.ACTIVE);
    org.mockito.Mockito.when(repository.findById(9L)).thenReturn(department);

    assertThat(service.get(9L)).isEqualTo(department);
    verify(authorization).requireRole(Role.SUPER_ADMIN);
  }
}
