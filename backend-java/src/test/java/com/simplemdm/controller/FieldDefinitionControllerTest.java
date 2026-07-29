package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmFieldDefinitionRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FieldDefinitionControllerTest {

    private MdmFieldDefinitionRepository repository;
    private FieldDefinitionController controller;

    @BeforeEach
    void setUp() {
        repository = mock(MdmFieldDefinitionRepository.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.getPermittedSystems(1L, "VIEW")).thenReturn(List.of("HR"));
        controller = new FieldDefinitionController(repository, permissions);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setDepartment("工程部");
        user.setRealName("王五");
        JwtInterceptor.CURRENT_USER.set(user);
    }

    @AfterEach
    void tearDown() {
        JwtInterceptor.CURRENT_USER.remove();
    }

    @Test
    void createRejectsDuplicateFieldKey() {
        when(repository.existsBySystemCodeAndDepartmentAndTableTypeAndSubTypeAndFieldKey(
            "HR", "工程部", "sub", "project", "project_name")).thenReturn(true);

        ApiResponse response = controller.create(body("project_name"));

        assertEquals(400, response.getCode());
        assertEquals("字段标识 project_name 已存在", response.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsInvalidFieldKey() {
        ApiResponse response = controller.create(body("项目名称"));

        assertEquals(400, response.getCode());
        assertEquals("field_key 只能使用小写英文字母、数字和下划线，且必须以字母开头", response.getMessage());
    }

    @Test
    void updateDoesNotChangeFieldKey() {
        MdmFieldDefinition existing = new MdmFieldDefinition();
        existing.setId(9L);
        existing.setDepartment("工程部");
        existing.setFieldKey("project_name");
        existing.setFieldName("项目名称");
        existing.setFieldType("string");
        existing.setRequired(true);
        existing.setSortOrder(1);
        when(repository.findById(9L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse response = controller.update(9L, Map.of(
            "field_key", "renamed_key",
            "field_name", "项目标题"
        ));

        assertEquals(200, response.getCode());
        assertEquals("project_name", existing.getFieldKey());
        assertEquals("项目标题", existing.getFieldName());
    }

    private Map<String, Object> body(String fieldKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("table_type", "sub");
        body.put("sub_type", "project");
        body.put("field_key", fieldKey);
        body.put("field_name", "项目名称");
        body.put("field_type", "string");
        body.put("required", true);
        body.put("sort_order", 1);
        body.put("options", List.of());
        return body;
    }
}
