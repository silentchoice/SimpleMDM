package com.example.mdm.auth;

import com.example.mdm.common.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class JdbcAuthenticationService implements AuthenticationService {
  private static final String UNKNOWN_USER_PASSWORD_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  JdbcAuthenticationService(NamedParameterJdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public UserPrincipal authenticate(String username, String password) {
    List<UserRow> users = jdbcTemplate.query("""
        SELECT u.id AS user_id, u.username, u.password_hash, u.display_name,
               d.id AS department_id, d.code AS department_code, d.name AS department_name,
               r.code AS role_code
          FROM users u
          LEFT JOIN departments d ON d.id = u.department_id AND d.status = 'ACTIVE'
          LEFT JOIN user_roles ur ON ur.user_id = u.id
          LEFT JOIN roles r ON r.id = ur.role_id AND r.status = 'ACTIVE'
         WHERE u.username = :username AND u.status = 'ACTIVE'
           AND (u.department_id IS NULL OR d.id IS NOT NULL)
        """, Map.of("username", username), (rs, rowNumber) -> new UserRow(
        rs.getLong("user_id"), rs.getString("username"), rs.getString("password_hash"),
        rs.getString("display_name"), (Long) rs.getObject("department_id"),
        rs.getString("department_code"), rs.getString("department_name"), rs.getString("role_code")));

    String passwordHash = users.isEmpty() ? UNKNOWN_USER_PASSWORD_HASH : users.get(0).passwordHash();
    if (!passwordEncoder.matches(password, passwordHash) || users.isEmpty()) {
      throw BusinessException.unauthorized();
    }

    UserRow first = users.get(0);
    List<Role> roles = new ArrayList<>();
    for (UserRow user : users) {
      if (user.roleCode() != null) {
        roles.add(Role.valueOf(user.roleCode()));
      }
    }
    DepartmentPrincipal department = first.departmentId() == null ? null
        : new DepartmentPrincipal(first.departmentId(), first.departmentCode(), first.departmentName());
    return new UserPrincipal(first.id(), first.username(), first.displayName(), department, roles);
  }

  private record UserRow(long id, String username, String passwordHash, String displayName,
                         Long departmentId, String departmentCode, String departmentName, String roleCode) {
  }
}
