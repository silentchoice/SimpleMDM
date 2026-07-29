package com.simplemdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.dto.DynamicPersonnelDTO;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.repository.MdmPersonnelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.springframework.data.domain.PageImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DynamicPersonnelServiceTest {

    private MdmPersonnelRepository repository;
    private DynamicFieldService fields;
    private PersonnelService service;

    @BeforeEach
    void setUp() {
        repository = mock(MdmPersonnelRepository.class);
        fields = mock(DynamicFieldService.class);
        service = new PersonnelService(repository, fields, new ObjectMapper());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsPendingPersonnelWithValidatedJsonData() throws Exception {
        Map<String, Object> validated = new LinkedHashMap<>();
        validated.put("employee_code", "EMP001");
        validated.put("name", "张三");
        when(fields.validate("HR", "工程部", "master", "basic", validated))
            .thenReturn(new DynamicFieldService.ValidationResult(validated));
        DynamicPersonnelDTO dto = new DynamicPersonnelDTO();
        dto.ownerDept = "工程部";
        dto.data = validated;

        MdmPersonnel saved = service.createFromApproval(dto, "HR");

        assertEquals("工程部", saved.getOwnerDept());
        assertEquals("HR", saved.getSystemCode());
        assertEquals("pending_approval", saved.getStatus());
        assertEquals(validated, new ObjectMapper().readValue(saved.getDataJson(), Map.class));
    }

    @Test
    void computesDiffForOwnerDepartmentAndBusinessFields() {
        MdmPersonnel existing = entity("工程部",
            "{\"employee_code\":\"EMP001\",\"name\":\"张三\"}");
        DynamicPersonnelDTO update = new DynamicPersonnelDTO();
        update.ownerDept = "产品部";
        update.data = Map.of("employee_code", "EMP001", "name", "张晓");
        when(fields.validate("HR", "产品部", "master", "basic", update.data))
            .thenReturn(new DynamicFieldService.ValidationResult(update.data));
        when(fields.computeDiff(anyMap(), eq(update.data))).thenReturn(Map.of(
            "name", Map.of("old", "张三", "new", "张晓")
        ));

        Map<String, Object> diff = service.computeDiff(existing, update);

        assertEquals(Map.of("old", "工程部", "new", "产品部"), diff.get("owner_dept"));
        assertEquals(Map.of("old", "张三", "new", "张晓"), diff.get("name"));
    }

    @Test
    void appliesApprovedChangesToJsonAndOwnerDepartment() throws Exception {
        MdmPersonnel existing = entity("工程部",
            "{\"employee_code\":\"EMP001\",\"name\":\"张三\"}");
        when(fields.validate(eq("HR"), eq("产品部"), eq("master"), eq("basic"), anyMap()))
            .thenAnswer(invocation ->
                new DynamicFieldService.ValidationResult(invocation.getArgument(4)));
        String changes = """
            {
              "owner_dept":{"old":"工程部","new":"产品部"},
              "name":{"old":"张三","new":"张晓"}
            }
            """;

        service.applyChanges(existing, changes);

        assertEquals("产品部", existing.getOwnerDept());
        Map<?, ?> data = new ObjectMapper().readValue(existing.getDataJson(), Map.class);
        assertEquals("张晓", data.get("name"));
        assertEquals("EMP001", data.get("employee_code"));
        assertEquals("active", existing.getStatus());
    }

    @Test
    void listUsesDynamicJsonAndOwnerDepartmentSearch() {
        when(repository.searchDynamic(eq("张三"), eq("工程部"), eq(List.of("工程部")),
            eq(false), eq("HR"), any())).thenReturn(new PageImpl<>(List.of()));

        service.listPersonnel("张三", "工程部", 1, 10, List.of("工程部"), "HR");

        verify(repository).searchDynamic(eq("张三"), eq("工程部"), eq(List.of("工程部")),
            eq(false), eq("HR"), any());
    }

    private MdmPersonnel entity(String ownerDept, String dataJson) {
        MdmPersonnel personnel = new MdmPersonnel();
        personnel.setSystemCode("HR");
        personnel.setOwnerDept(ownerDept);
        personnel.setDataJson(dataJson);
        personnel.setStatus("active");
        return personnel;
    }
}
