package com.simplemdm.repository;

import com.simplemdm.model.MdmPersonnel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MdmPersonnelRepository extends JpaRepository<MdmPersonnel, Long> {
    @Query("SELECT DISTINCT p.ownerDept FROM MdmPersonnel p WHERE p.ownerDept IS NOT NULL ORDER BY p.ownerDept")
    List<String> findDistinctOwnerDepartments();

    @Query("SELECT DISTINCT p.ownerDept FROM MdmPersonnel p WHERE p.systemCode = :systemCode AND p.ownerDept IS NOT NULL ORDER BY p.ownerDept")
    List<String> findDistinctOwnerDepartmentsBySystemCode(String systemCode);

    @Query("SELECT p FROM MdmPersonnel p WHERE " +
           "(:keyword IS NULL OR LOWER(p.dataJson) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:department IS NULL OR p.ownerDept = :department) " +
           "AND (:allScope = true OR p.ownerDept IN :allowedDepts) " +
           "AND (:systemCode IS NULL OR p.systemCode = :systemCode)")
    Page<MdmPersonnel> searchDynamic(String keyword, String department, List<String> allowedDepts,
                                     boolean allScope, String systemCode, Pageable pageable);
}
