package com.example.mdm.auth;

import com.example.mdm.common.error.BusinessException;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
  private final AccountStateRepository accountStates;

  public AuthorizationService(AccountStateRepository accountStates) {
    this.accountStates = accountStates;
  }

  public UserPrincipal requireRole(Role... allowed) {
    UserPrincipal principal = currentValidatedPrincipal();
    if (principal.roles().stream().noneMatch(new HashSet<>(Arrays.asList(allowed))::contains)) {
      throw BusinessException.forbidden();
    }
    return principal;
  }

  public UserPrincipal requireDepartment(long departmentId) {
    UserPrincipal principal = currentValidatedPrincipal();
    if (principal.roles().contains(Role.SUPER_ADMIN)) {
      return principal;
    }
    if (principal.department() == null || principal.department().id() != departmentId) {
      throw BusinessException.forbidden();
    }
    return principal;
  }

  private UserPrincipal currentValidatedPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      throw BusinessException.authenticationStateChanged();
    }
    AccountState current = accountStates.findActive(principal.id());
    Long tokenDepartment = principal.department() == null ? null : principal.department().id();
    if (current == null || !java.util.Objects.equals(current.departmentId(), tokenDepartment)
        || !new HashSet<>(current.roles()).equals(new HashSet<>(principal.roles()))) {
      throw BusinessException.authenticationStateChanged();
    }
    return principal;
  }
}
