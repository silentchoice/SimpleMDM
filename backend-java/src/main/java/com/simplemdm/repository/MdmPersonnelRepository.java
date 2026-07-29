package com.simplemdm.repository;

import com.simplemdm.model.MdmPersonnel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MdmPersonnelRepository extends JpaRepository<MdmPersonnel, Long> {
    Optional<MdmPersonnel> findByEmployeeCode(String employeeCode);

    @Query("SELECT DISTINCT p.department FROM MdmPersonnel p ORDER BY p.department")
    List<String> findDistinctDepartments();

    @Query("SELECT DISTINCT p.ownerDept FROM MdmPersonnel p WHERE p.ownerDept IS NOT NULL ORDER BY p.ownerDept")
    List<String> findDistinctOwnerDepartments();

    Page<MdmPersonnel> findByDepartmentIn(List<String> departments, Pageable pageable);

    @Query("SELECT p FROM MdmPersonnel p WHERE " +
           "(:keyword IS NULL OR p.name LIKE %:keyword% OR p.employeeCode LIKE %:keyword% OR p.position LIKE %:keyword%) " +
           "AND (:department IS NULL OR p.department = :department) " +
           "AND p.department IN :allowedDepts " +
           "AND (:systemCode IS NULL OR p.systemCode = :systemCode)")
    Page<MdmPersonnel> searchByKeywordAndDept(String keyword, String department, List<String> allowedDepts,
                                               String systemCode, Pageable pageable);

    @Query("SELECT p FROM MdmPersonnel p WHERE p.systemCode = :systemCode")
    Page<MdmPersonnel> findBySystemCode(String systemCode, Pageable pageable);

    @Query("SELECT p FROM MdmPersonnel p WHERE " +
           "(:keyword IS NULL OR LOWER(p.dataJson) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:department IS NULL OR p.ownerDept = :department) " +
           "AND (:allScope = true OR p.ownerDept IN :allowedDepts) " +
           "AND (:systemCode IS NULL OR p.systemCode = :systemCode)")
    Page<MdmPersonnel> searchDynamic(String keyword, String department, List<String> allowedDepts,
                                     boolean allScope, String systemCode, Pageable pageable);
}
