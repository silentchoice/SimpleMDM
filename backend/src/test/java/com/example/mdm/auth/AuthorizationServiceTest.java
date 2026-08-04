package com.example.mdm.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.mdm.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthorizationServiceTest {
  private final AccountStateRepository states = org.mockito.Mockito.mock(AccountStateRepository.class);
  private final AuthorizationService service = new AuthorizationService(states);

  @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

  @Test
  void rejectsAStaleTokenAfterRolesAreRevoked() {
    authenticate(new UserPrincipal(7L, "alice", "Alice", new DepartmentPrincipal(3L, "SALES", "Sales"),
        List.of(Role.DEPT_EDITOR)));
    when(states.findActive(7L)).thenReturn(new AccountState(7L, 3L, List.of(Role.DEPT_VIEWER)));

    assertThatThrownBy(() -> service.requireRole(Role.DEPT_EDITOR))
        .isInstanceOf(BusinessException.class).hasMessage("Authentication state changed");
  }

  @Test
  void editorCannotOperateAnotherDepartment() {
    authenticate(new UserPrincipal(7L, "alice", "Alice", new DepartmentPrincipal(3L, "SALES", "Sales"),
        List.of(Role.DEPT_EDITOR)));
    when(states.findActive(7L)).thenReturn(new AccountState(7L, 3L, List.of(Role.DEPT_EDITOR)));

    assertThatThrownBy(() -> service.requireDepartment(4L))
        .isInstanceOf(BusinessException.class).hasMessage("Forbidden");
  }

  private void authenticate(UserPrincipal principal) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }
}
