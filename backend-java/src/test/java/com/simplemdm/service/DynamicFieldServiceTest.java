package com.simplemdm.service;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.repository.MdmFieldDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicFieldServiceTest {

    private MdmFieldDefinitionRepository repository;
    private DynamicFieldService service;

    @BeforeEach
    void setUp() {
        repository = mock(MdmFieldDefinitionRepository.class);
        service = new DynamicFieldService(repository);
        when(repository.findBySystemCodeAndTableTypeOrderBySubTypeAscSortOrderAsc("HR", "master"))
            .thenReturn(List.of(
                field("employee_code", "工号", "string", true, null, 1),
                field("age", "年龄", "number", false, null, 2),
                field("hire_date", "入职日期", "date", false, null, 3),
                field("level", "职级", "select", false, "[\"P1\",\"P2\"]", 4)
            ));
    }

    @Test
    void rejectsUnknownFieldKey() {
        BusinessException error = assertThrows(BusinessException.class, () ->
            service.validate("HR", "工程部", "master", "basic",
                Map.of("employee_code", "EMP001", "extra", "x")));

        assertEquals("未知字段: extra", error.getMessage());
    }

    @Test
    void rejectsMissingRequiredValue() {
        BusinessException error = assertThrows(BusinessException.class, () ->
            service.validate("HR", "工程部", "master", "basic", Map.of("age", 20)));

        assertEquals("字段 employee_code 为必填项", error.getMessage());
    }

    @Test
    void coercesNumberAndAcceptsSelectOption() {
        DynamicFieldService.ValidationResult result = service.validate(
            "HR", "工程部", "master", "basic",
            Map.of("employee_code", "EMP001", "age", "20.5", "level", "P2"));

        assertEquals(new BigDecimal("20.5"), result.data().get("age"));
        assertEquals("P2", result.data().get("level"));
        assertEquals(List.of("employee_code", "age", "level"),
            result.data().keySet().stream().toList());
    }

    @Test
    void rejectsInvalidDateAndUnknownSelectOption() {
        BusinessException dateError = assertThrows(BusinessException.class, () ->
            service.validate("HR", "工程部", "master", "basic",
                Map.of("employee_code", "EMP001", "hire_date", "29/07/2026")));
        assertEquals("字段 hire_date 必须是日期 yyyy-MM-dd", dateError.getMessage());

        BusinessException optionError = assertThrows(BusinessException.class, () ->
            service.validate("HR", "工程部", "master", "basic",
                Map.of("employee_code", "EMP001", "level", "P9")));
        assertEquals("字段 level 的值不在允许选项中", optionError.getMessage());
    }

    @Test
    void computesDiffUsingStableFieldKeys() {
        Map<String, Object> oldData = new LinkedHashMap<>();
        oldData.put("employee_code", "EMP001");
        oldData.put("level", "P1");
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("employee_code", "EMP001");
        newData.put("level", "P2");

        Map<String, Object> diff = service.computeDiff(oldData, newData);

        assertEquals(Map.of("old", "P1", "new", "P2"), diff.get("level"));
        assertFalse(diff.containsKey("employee_code"));
    }

    @Test
    void visibleSubDefinitionsFilterSharedFieldsForForeignViewer() {
        MdmFieldDefinition privateField = field("private_cost", "成本", "number", false, null, 1);
        privateField.setShared(false);
        MdmFieldDefinition sharedField = field("project_name", "项目", "string", false, null, 2);
        sharedField.setShared(true);
        when(repository.findBySystemCodeAndDepartmentAndTableTypeAndSubTypeOrderBySortOrderAsc(
            "HR", "工程部", "sub", "project")).thenReturn(List.of(privateField, sharedField));

        assertEquals(List.of(sharedField),
            service.visibleSubDefinitions("HR", "工程部", "人力资源部", "project"));
        assertEquals(List.of(privateField, sharedField),
            service.visibleSubDefinitions("HR", "工程部", "工程部", "project"));
    }
    private MdmFieldDefinition field(String key, String name, String type, boolean required,
                                     String options, int sortOrder) {
        MdmFieldDefinition field = new MdmFieldDefinition();
        field.setFieldKey(key);
        field.setFieldName(name);
        field.setFieldType(type);
        field.setRequired(required);
        field.setOptionsJson(options);
        field.setSortOrder(sortOrder);
        return field;
    }
}
