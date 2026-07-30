package com.simplemdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.dto.PersonnelSubDTO;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.model.MdmPersonnelSub;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmPersonnelRepository;
import com.simplemdm.repository.MdmPersonnelSubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private MdmPersonnel parent() {
        MdmPersonnel parent = new MdmPersonnel();
        parent.setId(1L);
        parent.setSystemCode("HR");
        parent.setOwnerDept("工程部");
        return parent;
    }
}
