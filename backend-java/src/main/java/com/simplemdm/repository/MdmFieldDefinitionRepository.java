package com.simplemdm.repository;

import com.simplemdm.model.MdmFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MdmFieldDefinitionRepository extends JpaRepository<MdmFieldDefinition, Long> {
    boolean existsBySystemCodeAndDepartmentAndTableTypeAndSubTypeAndFieldKey(
        String systemCode, String department, String tableType, String subType, String fieldKey);
    List<MdmFieldDefinition> findBySystemCodeAndTableTypeOrderBySubTypeAscSortOrderAsc(
        String systemCode, String tableType);
    List<MdmFieldDefinition> findBySystemCodeAndDepartmentAndTableTypeAndSubTypeOrderBySortOrderAsc(
        String systemCode, String department, String tableType, String subType);
    List<MdmFieldDefinition> findByDepartmentAndSubTypeOrderBySortOrder(String department, String subType);
    List<MdmFieldDefinition> findByDepartmentAndTableTypeAndSystemCodeOrderBySubTypeAscSortOrder(String department, String tableType, String systemCode);
    List<MdmFieldDefinition> findByDepartmentAndSubTypeAndSystemCodeOrderBySortOrder(String department, String subType, String systemCode);
    List<MdmFieldDefinition> findByDepartmentAndSystemCodeOrderBySubTypeAscSortOrder(String department, String systemCode);
    List<MdmFieldDefinition> findByTableTypeAndSystemCodeOrderBySubTypeAscSortOrder(String tableType, String systemCode);

    @Query("SELECT DISTINCT f.subType FROM MdmFieldDefinition f WHERE f.department = :department AND f.systemCode = :systemCode ORDER BY f.subType")
    List<String> findDistinctSubTypesByDepartmentAndSystemCode(String department, String systemCode);

    @Query("SELECT DISTINCT f.subType FROM MdmFieldDefinition f WHERE f.department = :department AND f.tableType = :tableType AND f.systemCode = :systemCode ORDER BY f.subType")
    List<String> findDistinctSubTypesByDepartmentAndTableTypeAndSystemCode(String department, String tableType, String systemCode);

    @Query("SELECT DISTINCT f.subType FROM MdmFieldDefinition f WHERE f.tableType = :tableType AND f.systemCode = :systemCode ORDER BY f.subType")
    List<String> findDistinctSubTypesByTableTypeAndSystemCode(String tableType, String systemCode);

    @Query("SELECT DISTINCT f.department FROM MdmFieldDefinition f WHERE f.systemCode = :systemCode AND f.department IS NOT NULL ORDER BY f.department")
    List<String> findDistinctDepartmentsBySystemCode(String systemCode);

    void deleteByDepartmentAndSubType(String department, String subType);
}
