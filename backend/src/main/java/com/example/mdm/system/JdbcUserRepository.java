package com.example.mdm.system;

import com.example.mdm.auth.Role;
import com.example.mdm.common.error.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

@Repository
class JdbcUserRepository implements UserRepository {
  private final NamedParameterJdbcTemplate jdbc;
  JdbcUserRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override @Transactional
  public SystemUser create(String username, String passwordHash, String displayName, Long departmentId, List<Role> roles) {
    var key = new GeneratedKeyHolder();
    var params = new MapSqlParameterSource().addValue("username", username).addValue("password", passwordHash)
        .addValue("display", displayName).addValue("department", departmentId);
    jdbc.update("INSERT INTO users(username,password_hash,display_name,department_id,status) VALUES(:username,:password,:display,:department,'ACTIVE')",
        params, key);
    long id = key.getKey().longValue();
    replaceRoles(id, roles);
    return new SystemUser(id, username, displayName, departmentId, EntityStatus.ACTIVE, roles);
  }

  @Override public SystemUser update(long id, String displayName, Long departmentId) {
    int changed = jdbc.update("UPDATE users SET display_name=:display,department_id=:department WHERE id=:id",
        new MapSqlParameterSource().addValue("id", id).addValue("display", displayName).addValue("department", departmentId));
    if (changed == 0) throw BusinessException.notFound("User");
    return findAll().stream().filter(user -> user.id() == id).findFirst().orElseThrow();
  }

  @Override public void setStatus(long id, EntityStatus status) {
    if (jdbc.update("UPDATE users SET status=:status WHERE id=:id", Map.of("id", id, "status", status.name())) == 0)
      throw BusinessException.notFound("User");
  }

  @Override @Transactional public void assignRoles(long id, List<Role> roles) { replaceRoles(id, roles); }

  private void replaceRoles(long id, List<Role> roles) {
    jdbc.update("DELETE FROM user_roles WHERE user_id=:id", Map.of("id", id));
    for (Role role : roles) {
      int changed = jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT :id,id FROM roles WHERE code=:role AND status='ACTIVE'",
          Map.of("id", id, "role", role.name()));
      if (changed == 0) throw BusinessException.notFound("Role");
    }
  }

  @Override public List<SystemUser> findAll() {
    var rows = jdbc.queryForList("""
        SELECT u.id,u.username,u.display_name,u.department_id,u.status,r.code role_code
          FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id LEFT JOIN roles r ON r.id=ur.role_id
         ORDER BY u.id,r.code
        """, Map.of());
    Map<Long, MutableUser> users = new LinkedHashMap<>();
    for (Map<String,Object> row : rows) {
      long id = ((Number) row.get("id")).longValue();
      MutableUser user = users.computeIfAbsent(id, ignored -> new MutableUser(id, (String) row.get("username"),
          (String) row.get("display_name"), row.get("department_id") == null ? null : ((Number) row.get("department_id")).longValue(),
          EntityStatus.valueOf((String) row.get("status"))));
      if (row.get("role_code") != null) user.roles.add(Role.valueOf((String) row.get("role_code")));
    }
    return users.values().stream().map(MutableUser::view).toList();
  }

  @Override public boolean exists(long id) {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=:id", Map.of("id", id), Integer.class);
    return count != null && count > 0;
  }

  @Override public boolean isDepartmentActive(long departmentId) {
    return !jdbc.queryForList("SELECT id FROM departments WHERE id=:id AND status='ACTIVE' FOR UPDATE",
        Map.of("id", departmentId), Long.class).isEmpty();
  }

  @Override @Transactional
  public void createInitialAdministrator(String username, String passwordHash, String displayName) {
    for (Role role : Role.values()) jdbc.update("INSERT IGNORE INTO roles(code,name,status) VALUES(:code,:name,'ACTIVE')",
        Map.of("code", role.name(), "name", role.name()));
    if (usernameExists(username)) {
      if (isUsableSuperAdmin(username)) return;
      throw new IllegalStateException("Initial administrator username already belongs to a non-admin user");
    }
    try {
      create(username, passwordHash, displayName, null, List.of(Role.SUPER_ADMIN));
    } catch (DuplicateKeyException concurrentInsert) {
      if (!isUsableSuperAdmin(username)) {
        throw new IllegalStateException("Initial administrator username was concurrently claimed", concurrentInsert);
      }
    }
  }

  private boolean usernameExists(String username) {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=:username",
        Map.of("username", username), Integer.class);
    return count != null && count > 0;
  }

  private boolean isUsableSuperAdmin(String username) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM users u
          LEFT JOIN departments d ON d.id=u.department_id AND d.status='ACTIVE'
          JOIN user_roles ur ON ur.user_id=u.id
          JOIN roles r ON r.id=ur.role_id AND r.status='ACTIVE'
         WHERE u.username=:username AND u.status='ACTIVE' AND r.code='SUPER_ADMIN'
           AND (u.department_id IS NULL OR d.id IS NOT NULL)
        """, Map.of("username", username), Integer.class);
    return count != null && count > 0;
  }

  private static final class MutableUser {
    final long id; final String username; final String display; final Long department; final EntityStatus status;
    final List<Role> roles = new ArrayList<>();
    MutableUser(long id,String username,String display,Long department,EntityStatus status) {
      this.id=id;this.username=username;this.display=display;this.department=department;this.status=status;
    }
    SystemUser view(){return new SystemUser(id,username,display,department,status,roles);}
  }
}
