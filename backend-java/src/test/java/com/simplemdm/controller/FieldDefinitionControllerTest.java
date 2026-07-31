package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmFieldDefinitionRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.FieldDefinitionService;
import com.simplemdm.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FieldDefinitionControllerTest {
    private FieldDefinitionService service;
    private FieldDefinitionController controller;
    private SysUser user;

    @BeforeEach
    void setUp() {
        MdmFieldDefinitionRepository repository = mock(MdmFieldDefinitionRepository.class);
        PermissionService permissions = mock(PermissionService.class);
        service = mock(FieldDefinitionService.class);
        when(permissions.getPermittedSystems(1L, "VIEW")).thenReturn(List.of("HR"));
        controller = new FieldDefinitionController(repository, permissions, service);
        user = new SysUser();
        user.setId(1L);
        user.setDepartment("工程部");
        user.setRealName("王五");
        JwtInterceptor.LEGACY_CURRENT_USER.set(user);
    }

    @AfterEach void tearDown() { JwtInterceptor.LEGACY_CURRENT_USER.remove(); }

    @Test
    void createDelegatesToService() {
        Map<String,Object> body = Map.of("field_key", "project_name");
        MdmFieldDefinition definition = definition();
        when(service.create(body, user, "HR")).thenReturn(definition);
        ApiResponse response = controller.create(body);
        assertEquals(200, response.getCode());
        assertEquals("字段已创建", response.getMessage());
        verify(service).create(body, user, "HR");
    }

    @Test
    void updateDelegatesToService() {
        Map<String,Object> body = Map.of("field_name", "项目标题");
        when(service.update(9L, body, user)).thenReturn(definition());
        ApiResponse response = controller.update(9L, body);
        assertEquals(200, response.getCode());
        assertEquals("字段已更新", response.getMessage());
        verify(service).update(9L, body, user);
    }

    @Test
    void deleteDelegatesToTransactionalService() {
        ApiResponse response = controller.delete(9L);
        assertEquals(200, response.getCode());
        assertEquals("字段及历史数据已删除", response.getMessage());
        verify(service).deleteSubField(9L, user);
    }

    private MdmFieldDefinition definition() {
        MdmFieldDefinition f = new MdmFieldDefinition();
        f.setId(9L); f.setDepartment("工程部"); f.setTableType("sub"); f.setSubType("project");
        f.setFieldKey("project_name"); f.setFieldName("项目名称"); f.setFieldType("string");
        f.setRequired(false); f.setSortOrder(1); f.setSystemField(false); f.setShared(true);
        return f;
    }
}