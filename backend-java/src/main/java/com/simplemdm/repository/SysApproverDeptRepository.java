package com.simplemdm.repository;

import com.simplemdm.model.SysApproverDept;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysApproverDeptRepository extends JpaRepository<SysApproverDept, Long> {
    List<SysApproverDept> findByUserId(Long userId);
    List<SysApproverDept> findByDepartment(String department);
    List<SysApproverDept> findByUserIdAndDepartment(Long userId, String department);
    void deleteByUserIdAndDepartment(Long userId, String department);
}
