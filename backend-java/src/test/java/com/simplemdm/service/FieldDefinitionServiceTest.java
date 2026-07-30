package com.simplemdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.model.MdmPersonnelSub;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmFieldDefinitionRepository;
import com.simplemdm.repository.MdmPersonnelSubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FieldDefinitionServiceTest {
    private MdmFieldDefinitionRepository fields;
    private MdmPersonnelSubRepository records;
    private PermissionService permissions;
    private FieldDefinitionService service;

    @BeforeEach
    void setUp() {
        fields = mock(MdmFieldDefinitionRepository.class);
        records = mock(MdmPersonnelSubRepository.class);
        permissions = mock(PermissionService.class);
        service = new FieldDefinitionService(fields, records, permissions, new ObjectMapper());
    }

    @Test
    void createReportsExistingMasterFieldWhenSubKeyConflicts() {
        MdmFieldDefinition conflict = field("ALL", "master", "basic", "employee_code");
        when(fields.findBySystemCodeAndFieldKey("HR", "employee_code")).thenReturn(Optional.of(conflict));
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.create(subBody("employee_code", true), departmentAdmin(), "HR"));
        assertEquals(400, error.getCode());
        assertEquals("字段标识 employee_code 已被主表使用", error.getMessage());
    }

    @Test
    void createRejectsSharedMasterField() {
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.create(masterBody("new_master_key", true), departmentAdmin(), "HR"));
        assertEquals("主表字段不能设置共享", error.getMessage());
    }

    @Test
    void deleteSubFieldRemovesDefinitionAndHistoricalJsonValues() {
        MdmFieldDefinition definition = field("工程部", "sub", "salary", "engineering_base_pay");
        MdmPersonnelSub first = record("{\"engineering_base_pay\":25000,\"engineering_bonus\":5000}");
        MdmPersonnelSub second = record("{\"engineering_base_pay\":30000}");
        when(fields.findById(9L)).thenReturn(Optional.of(definition));
        when(permissions.getEditableDepts(7L)).thenReturn(List.of("工程部"));
        when(records.findByOwnerDeptAndSubType("工程部", "salary")).thenReturn(List.of(first, second));

        service.deleteSubField(9L, departmentAdmin());

        assertEquals("{\"engineering_bonus\":5000}", first.getDataJson());
        assertEquals("{}", second.getDataJson());
        verify(records).saveAll(List.of(first, second));
        verify(fields).delete(definition);
    }

    @Test void rejectsMasterDeletion() { assertDeleteMessage(field("ALL", "master", "basic", "x"), departmentAdmin(), "主表字段不可删除"); }
    @Test void rejectsSystemFieldDeletion() { MdmFieldDefinition f=field("工程部","sub","salary","x"); f.setSystemField(true); assertDeleteMessage(f, departmentAdmin(), "系统字段不可删除"); }
    @Test void rejectsMainAdministratorDeletion() { SysUser u=departmentAdmin(); u.setIsAdmin(true); assertDeleteMessage(field("工程部","sub","salary","x"),u,"主管理员无字段删除权限"); }
    @Test void rejectsAnotherDepartmentDeletion() { assertDeleteMessage(field("产品部","sub","salary","x"),departmentAdmin(),"只能删除本部门子表字段"); }
    @Test void rejectsWithoutOwnDepartmentEditPermission() { when(permissions.getEditableDepts(7L)).thenReturn(List.of()); assertDeleteMessage(field("工程部","sub","salary","x"),departmentAdmin(),"无本部门字段删除权限"); }

    @Test
    void malformedHistoryPreventsDefinitionDeletion() {
        MdmFieldDefinition definition = field("工程部", "sub", "salary", "x");
        when(fields.findById(9L)).thenReturn(Optional.of(definition));
        when(permissions.getEditableDepts(7L)).thenReturn(List.of("工程部"));
        when(records.findByOwnerDeptAndSubType("工程部", "salary")).thenReturn(List.of(record("not-json")));
        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteSubField(9L, departmentAdmin()));
        assertEquals("历史子表数据无法清理", error.getMessage());
        verify(fields, never()).delete(any());
    }

    private void assertDeleteMessage(MdmFieldDefinition definition, SysUser user, String message) {
        when(fields.findById(9L)).thenReturn(Optional.of(definition));
        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteSubField(9L, user));
        assertEquals(403, error.getCode());
        assertEquals(message, error.getMessage());
    }

    private SysUser departmentAdmin() { SysUser u=new SysUser(); u.setId(7L); u.setDepartment("工程部"); u.setRealName("王五"); u.setIsAdmin(false); return u; }
    private MdmFieldDefinition field(String dept,String table,String sub,String key) { MdmFieldDefinition f=new MdmFieldDefinition(); f.setDepartment(dept); f.setTableType(table); f.setSubType(sub); f.setFieldKey(key); f.setFieldName(key); f.setFieldType("string"); f.setRequired(false); f.setSortOrder(1); f.setSystemField(false); f.setShared(false); return f; }
    private MdmPersonnelSub record(String json) { MdmPersonnelSub r=new MdmPersonnelSub(); r.setDataJson(json); return r; }
    private Map<String,Object> subBody(String key, boolean shared) { Map<String,Object> b=baseBody(key); b.put("table_type","sub"); b.put("shared",shared); return b; }
    private Map<String,Object> masterBody(String key, boolean shared) { Map<String,Object> b=baseBody(key); b.put("table_type","master"); b.put("shared",shared); return b; }
    private Map<String,Object> baseBody(String key) { Map<String,Object> b=new HashMap<>(); b.put("sub_type","basic"); b.put("field_key",key); b.put("field_name",key); b.put("field_type","string"); b.put("required",false); b.put("sort_order",1); return b; }
}