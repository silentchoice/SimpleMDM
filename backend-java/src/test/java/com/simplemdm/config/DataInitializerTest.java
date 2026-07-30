package com.simplemdm.config;

import org.junit.jupiter.api.Test;
import com.simplemdm.repository.*;
import com.simplemdm.service.AuthService;
import com.simplemdm.model.MdmFieldDefinition;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataInitializerTest {
    private SysUserRepository users;
    private MdmPersonnelRepository personnel;
    private WfApprovalRepository approvals;
    private SysPushLogRepository pushLogs;
    private SysPushApiRepository pushApis;
    private SysUserPermissionRepository permissions;
    private SysApproverDeptRepository approvers;
    private MdmPersonnelSubRepository subRecords;
    private MdmFieldDefinitionRepository fieldDefinitions;
    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        users=mock(SysUserRepository.class); personnel=mock(MdmPersonnelRepository.class);
        approvals=mock(WfApprovalRepository.class); pushLogs=mock(SysPushLogRepository.class);
        pushApis=mock(SysPushApiRepository.class); permissions=mock(SysUserPermissionRepository.class);
        approvers=mock(SysApproverDeptRepository.class); subRecords=mock(MdmPersonnelSubRepository.class);
        fieldDefinitions=mock(MdmFieldDefinitionRepository.class);
        initializer = new DataInitializer(users, personnel, approvals, pushLogs, pushApis,
            permissions, approvers, subRecords, fieldDefinitions, mock(AuthService.class), false);
    }
    @Test
    void demoSchemaIsCurrentWhenEveryRequiredFieldExists() {
        for (DataInitializer.DemoField field : DataInitializer.demoFields()) {
            when(fieldDefinitions.findBySystemCodeAndFieldKey(field.systemCode(), field.fieldKey()))
                .thenReturn(Optional.of(new MdmFieldDefinition()));
        }
        assertTrue(initializer.demoSchemaIsCurrent());
    }

    @Test
    void demoSchemaNeedsRefreshWhenARequiredFieldIsMissing() {
        DataInitializer.DemoField first = DataInitializer.demoFields().get(0);
        when(fieldDefinitions.findBySystemCodeAndFieldKey(first.systemCode(), first.fieldKey()))
            .thenReturn(Optional.empty());
        assertTrue(!initializer.demoSchemaIsCurrent());
    }
    @Test
    void resetBusinessDemoDataDeletesOnlyBusinessTablesInDependencyOrder() {
        initializer.resetBusinessDemoData();
        org.mockito.InOrder order = inOrder(pushLogs, approvals, subRecords, personnel, fieldDefinitions);
        order.verify(pushLogs).deleteAllInBatch();
        order.verify(approvals).deleteAllInBatch();
        order.verify(subRecords).deleteAllInBatch();
        order.verify(personnel).deleteAllInBatch();
        order.verify(fieldDefinitions).deleteAllInBatch();
        verify(users, never()).deleteAllInBatch();
        verify(permissions, never()).deleteAllInBatch();
    }
    @Test
    void demoFieldKeysAreUniqueWithinSystem() {
        List<DataInitializer.DemoField> fields = DataInitializer.demoFields();
        long unique = fields.stream()
            .map(field -> field.systemCode() + ":" + field.fieldKey())
            .distinct()
            .count();
        assertEquals(fields.size(), unique);
    }

    @Test
    void onlySubFieldsCanBeShared() {
        assertTrue(DataInitializer.demoFields().stream()
            .filter(DataInitializer.DemoField::shared)
            .allMatch(field -> "sub".equals(field.tableType())));
    }

    @Test
    void payrollFieldsRemainPrivate() {
        assertTrue(DataInitializer.demoFields().stream()
            .filter(field -> "payroll".equals(field.subType()))
            .noneMatch(DataInitializer.DemoField::shared));
    }
}