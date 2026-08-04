package com.example.mdm.auth;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountStateRepository implements AccountStateRepository {
  private final NamedParameterJdbcTemplate jdbc;

  JdbcAccountStateRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  public AccountState findActive(long userId) {
    List<Row> rows = jdbc.query("""
        SELECT u.id, u.department_id, d.id AS active_department_id, r.code AS role_code
          FROM users u
          LEFT JOIN departments d ON d.id=u.department_id AND d.status='ACTIVE'
          LEFT JOIN user_roles ur ON ur.user_id=u.id
          LEFT JOIN roles r ON r.id=ur.role_id AND r.status='ACTIVE'
         WHERE u.id=:id AND u.status='ACTIVE'
        """, Map.of("id", userId), (rs, n) -> row(rs));
    if (rows.isEmpty()) return null;
    Row first = rows.get(0);
    if (first.departmentId != null && first.activeDepartmentId == null) return null;
    List<Role> roles = new ArrayList<>();
    for (Row row : rows) if (row.roleCode != null) roles.add(Role.valueOf(row.roleCode));
    return new AccountState(first.id, first.departmentId, roles);
  }

  private Row row(ResultSet rs) throws java.sql.SQLException {
    return new Row(rs.getLong("id"), (Long) rs.getObject("department_id"),
        (Long) rs.getObject("active_department_id"), rs.getString("role_code"));
  }

  private record Row(long id, Long departmentId, Long activeDepartmentId, String roleCode) {}
}
