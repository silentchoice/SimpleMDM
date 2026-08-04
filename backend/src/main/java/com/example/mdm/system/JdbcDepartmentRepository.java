package com.example.mdm.system;

import com.example.mdm.common.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDepartmentRepository implements DepartmentRepository {
  private final NamedParameterJdbcTemplate jdbc;
  JdbcDepartmentRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override public Department create(String code, String name) {
    var key = new GeneratedKeyHolder();
    jdbc.update("INSERT INTO departments(code,name,status) VALUES(:code,:name,'ACTIVE')",
        new MapSqlParameterSource(Map.of("code", code, "name", name)), key);
    return new Department(key.getKey().longValue(), code, name, EntityStatus.ACTIVE);
  }

  @Override public Department update(long id, String code, String name) {
    int changed = jdbc.update("UPDATE departments SET code=:code,name=:name WHERE id=:id",
        Map.of("id", id, "code", code, "name", name));
    if (changed == 0) throw BusinessException.notFound("Department");
    return jdbc.queryForObject("SELECT id,code,name,status FROM departments WHERE id=:id", Map.of("id", id),
        (rs, n) -> new Department(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
            EntityStatus.valueOf(rs.getString("status"))));
  }

  @Override public void setStatus(long id, EntityStatus status) {
    if (jdbc.update("UPDATE departments SET status=:status WHERE id=:id", Map.of("id", id, "status", status.name())) == 0)
      throw BusinessException.notFound("Department");
  }

  @Override public List<Department> findAll() {
    return jdbc.query("SELECT id,code,name,status FROM departments ORDER BY id", Map.of(),
        (rs, n) -> new Department(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
            EntityStatus.valueOf(rs.getString("status"))));
  }

  @Override public Department findById(long id) {
    List<Department> results = jdbc.query("SELECT id,code,name,status FROM departments WHERE id=:id", Map.of("id", id),
        (rs, n) -> new Department(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
            EntityStatus.valueOf(rs.getString("status"))));
    if (results.isEmpty()) throw BusinessException.notFound("Department");
    return results.get(0);
  }
}
