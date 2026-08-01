package com.simplemdm.repository.system;

import com.simplemdm.model.system.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Query("select d from Department d where d.system.id = :systemId and d.code = :code")
    Optional<Department> findBySystemIdAndCode(Long systemId, String code);

    List<Department> findByPathStartingWith(String path);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "parent")
    List<Department> findBySystem_Id(Long systemId);

    @Query("select d from Department d where d.system.id = :systemId and d.id = :id and d.status = 'active'")
    Optional<Department> findActiveBySystemIdAndId(Long systemId, Long id);

    @Query("select d.id from Department d where d.system.id = :systemId and d.status = 'active' order by d.id")
    List<Long> findActiveIdsBySystemId(Long systemId);
}
