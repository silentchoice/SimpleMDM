package com.example.mdm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {
  @Test
  void creatingUserHashesPasswordAndRequiresSuperAdmin() {
    var authorization = org.mockito.Mockito.mock(AuthorizationService.class);
    var repository = org.mockito.Mockito.mock(UserRepository.class);
    var encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    var service = new UserService(repository, authorization, encoder);
    var saved = new SystemUser(5L, "alice", "Alice", 3L, EntityStatus.ACTIVE,
        List.of(Role.DEPT_EDITOR));
    org.mockito.Mockito.when(encoder.encode("correct-password")).thenReturn("bcrypt-hash");
    org.mockito.Mockito.when(repository.isDepartmentActive(3L)).thenReturn(true);
    org.mockito.Mockito.when(repository.create("alice", "bcrypt-hash", "Alice", 3L,
        List.of(Role.DEPT_EDITOR))).thenReturn(saved);

    assertThat(service.create("alice", "correct-password", "Alice", 3L,
        List.of(Role.DEPT_EDITOR))).isEqualTo(saved);

    verify(authorization).requireRole(Role.SUPER_ADMIN);
    verify(repository).create("alice", "bcrypt-hash", "Alice", 3L, List.of(Role.DEPT_EDITOR));
  }

  @Test
  void rejectsMissingRolesAsBadRequest() {
    var service = new UserService(org.mockito.Mockito.mock(UserRepository.class),
        org.mockito.Mockito.mock(AuthorizationService.class),
        org.mockito.Mockito.mock(PasswordEncoder.class));

    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> service.create("alice", "password", "Alice", 3L, null))
        .isInstanceOf(com.example.mdm.common.error.BusinessException.class)
        .hasMessage("Roles are required");
  }

  @Test
  void rejectsDisabledTargetDepartment() {
    var authorization = org.mockito.Mockito.mock(AuthorizationService.class);
    var repository = org.mockito.Mockito.mock(UserRepository.class);
    var service = new UserService(repository, authorization, org.mockito.Mockito.mock(PasswordEncoder.class));
    org.mockito.Mockito.when(repository.isDepartmentActive(3L)).thenReturn(false);

    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> service.create("alice", "password", "Alice", 3L, List.of(Role.DEPT_EDITOR)))
        .isInstanceOf(com.example.mdm.common.error.BusinessException.class)
        .hasMessage("Department not found");
  }
}
