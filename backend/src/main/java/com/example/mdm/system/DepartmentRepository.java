package com.example.mdm.system;

import java.util.List;

public interface DepartmentRepository {
  Department create(String code, String name);
  Department update(long id, String code, String name);
  void setStatus(long id, EntityStatus status);
  List<Department> findAll();
  Department findById(long id);
}
