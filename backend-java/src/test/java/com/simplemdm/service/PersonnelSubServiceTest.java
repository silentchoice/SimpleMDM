package com.simplemdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.dto.PersonnelSubDTO;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.model.MdmPersonnelSub;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmPersonnelRepository;
import com.simplemdm.repository.MdmPersonnelSubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonnelSubServiceTest {

    private MdmPersonnelSubRepository subRepository;
    private MdmPersonnelRepository personnelRepository;
    private DynamicFieldService fields;
    private PersonnelSubService service;
    private PermissionService permissions;
    private SysUser editor;

    @BeforeEach
    void setUp() {
        subRepository = mock(MdmPersonnelSubRepository.class);
        personnelRepository = mock(MdmPersonnelRepository.class);
        fields = mock(DynamicFieldService.class);
        permissions = mock(PermissionService.class);
        service = new PersonnelSubService(
            subRepository, personnelRepository, fields, new ObjectMapper(), permissions);
        editor = new SysUser();
        editor.setId(7L);
        editor.setDepartment("工程部");
        when(permissions.getEditableDepts(7L)).thenReturn(java.util.List.of("工程部"));
        when(subRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsValidatedRecordForDefinedSubType() throws Exception {
        MdmPersonnel parent = parent();
        when(personnelRepository.findById(1L)).thenReturn(Optional.of(parent));
        Map<String, Object> data = Map.of("project_name", "智能工厂");
        when(fields.validate("HR", "工程部", "sub", "project", data))
            .thenReturn(new DynamicFieldService.ValidationResult(data));
        PersonnelSubDTO dto = new PersonnelSubDTO();
        dto.subType = "project";
        dto.data = data;

        Map<String, Object> result = service.create(1L, dto, editor);

        assertEquals(data, result.get("data"));
        assertEquals("工程部", result.get("owner_dept"));
        assertEquals("project", result.get("sub_type"));
    }

    @Test
    void rejectsEditorFromAnotherDepartment() {
        when(personnelRepository.findById(1L)).thenReturn(Optional.of(parent()));
        editor.setDepartment("产品部");
        when(permissions.getEditableDepts(7L)).thenReturn(java.util.List.of("产品部"));
        PersonnelSubDTO dto = new PersonnelSubDTO();
        dto.subType = "project";
        dto.data = Map.of("project_name", "智能工厂");

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.create(1L, dto, editor));

        assertEquals(403, error.getCode());
    }

    @Test
    void anotherDepartmentReceivesOnlySharedSubFields() {
        when(personnelRepository.findById(1L)).thenReturn(Optional.of(parent()));
        MdmPersonnelSub record = record("{\"engineering_project_name\":\"工厂平台\",\"engineering_internal_cost\":9000}");
        when(subRepository.findByPersonnelId(1L)).thenReturn(List.of(record));
        editor.setDepartment("人力资源部");
        when(fields.visibleSubDefinitions("HR", "工程部", "人力资源部", "project"))
            .thenReturn(List.of(field("engineering_project_name", true)));

        List<Map<String,Object>> result = service.list(1L, editor);

        assertEquals(Map.of("engineering_project_name", "工厂平台"), result.get(0).get("data"));
        assertFalse(result.get(0).containsKey("visibility"));
    }

    @Test
    void ownerDepartmentReceivesAllDefinedFields() {
        when(personnelRepository.findById(1L)).thenReturn(Optional.of(parent()));
        when(subRepository.findByPersonnelId(1L)).thenReturn(List.of(record("{\"a\":1,\"b\":2,\"stale\":3}")));
        when(fields.visibleSubDefinitions("HR", "工程部", "工程部", "project"))
            .thenReturn(List.of(field("a", false), field("b", true)));
        List<Map<String,Object>> result = service.list(1L, editor);
        assertEquals(Map.of("a", 1, "b", 2), result.get(0).get("data"));
    }

    @Test
    void foreignViewerReceivesNoGroupWithoutSharedFields() {
        when(personnelRepository.findById(1L)).thenReturn(Optional.of(parent()));
        when(subRepository.findByPersonnelId(1L)).thenReturn(List.of(record("{\"private_cost\":9}")));
        editor.setDepartment("人力资源部");
        when(fields.visibleSubDefinitions("HR", "工程部", "人力资源部", "project")).thenReturn(List.of());
        assertTrue(service.list(1L, editor).isEmpty());
    }

    private MdmPersonnelSub record(String json) {
        MdmPersonnelSub r = new MdmPersonnelSub();
        r.setPersonnelId(1L); r.setSystemCode("HR"); r.setOwnerDept("工程部");
        r.setSubType("project"); r.setDataJson(json); r.setVersion(1); r.setVisibility("private");
        return r;
    }

    private MdmFieldDefinition field(String key, boolean shared) {
        MdmFieldDefinition f = new MdmFieldDefinition(); f.setFieldKey(key); f.setShared(shared); return f;
    }
    private MdmPersonnel parent() {
        MdmPersonnel parent = new MdmPersonnel();
        parent.setId(1L);
        parent.setSystemCode("HR");
        parent.setOwnerDept("工程部");
        return parent;
    }
}
