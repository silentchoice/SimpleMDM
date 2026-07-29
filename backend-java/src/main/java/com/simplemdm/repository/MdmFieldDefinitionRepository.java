package com.simplemdm.repository;

import com.simplemdm.model.MdmFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MdmFieldDefinitionRepository extends JpaRepository<MdmFieldDefinition, Long> {
    List<MdmFieldDefinition> findByDepartmentAndSubTypeOrderBySortOrder(String department, String subType);
    List<MdmFieldDefinition> findByDepartmentOrderBySubTypeAscSortOrder(String department);

    @Query("SELECT DISTINCT f.subType FROM MdmFieldDefinition f WHERE f.department = :department ORDER BY f.subType")
    List<String> findDistinctSubTypesByDepartment(String department);

    void deleteByDepartmentAndSubType(String department, String subType);
}
