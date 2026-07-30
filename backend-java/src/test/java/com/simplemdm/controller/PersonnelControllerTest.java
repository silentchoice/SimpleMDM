package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.DynamicPersonnelDTO;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.model.SysUser;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.ApprovalService;
import com.simplemdm.service.PermissionService;
import com.simplemdm.service.PersonnelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonnelControllerTest {
    private PersonnelService personnelService;
    private ApprovalService approvalService;
    private PermissionService permissionService;
    private PersonnelController controller;
    private SysUser user;

    @BeforeEach
    void setUp() {
        personnelService = mock(PersonnelService.class);
        approvalService = mock(ApprovalService.class);
        permissionService = mock(PermissionService.class);
        controller = new PersonnelController(personnelService, approvalService, permissionService);
        user = new SysUser();
        user.setId(7L); user.setDepartment("工程部"); user.setIsAdmin(false);
        JwtInterceptor.CURRENT_USER.set(user);
        when(permissionService.getPermittedSystems(7L, "VIEW")).thenReturn(List.of("HR"));
        when(permissionService.getPermittedSystems(7L, "EDIT")).thenReturn(List.of("HR"));
    }

    @AfterEach void tearDown() { JwtInterceptor.CURRENT_USER.remove(); }

    @Test
    void listRejectsMissingDepartment() {
        ApiResponse response = controller.list("", "", 1, 10);
        assertEquals(400, response.getCode());
        assertEquals("必须选择部门", response.getMessage());
        verify(personnelService, never()).listPersonnel(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void listRejectsDepartmentWithoutViewPermission() {
        when(permissionService.getConcreteViewableDepts(7L, "HR"))
            .thenReturn(List.of("工程部", "产品部"));
        ApiResponse response = controller.list("", "市场部", 1, 10);
        assertEquals(403, response.getCode());
    }

    @Test
    void detailRejectsDirectIdAccessToHiddenDepartment() {
        when(personnelService.requireViewablePersonnel(9L, user))
            .thenThrow(new BusinessException(403, "无权查看该部门主数据"));
        ApiResponse response = controller.get(9L);
        assertEquals(403, response.getCode());
    }

    @Test
    void createRejectsAnotherDepartmentEvenWithBroadEditPermission() {
        when(permissionService.getEditableDepts(7L)).thenReturn(null);
        DynamicPersonnelDTO dto = dto("产品部");
        ApiResponse response = controller.create(dto);
        assertEquals(403, response.getCode());
        assertEquals("只能维护所属部门主数据", response.getMessage());
        verifyNoInteractions(approvalService);
    }

    @Test
    void updateRejectsChangingOrMaintainingAnotherDepartment() {
        when(personnelService.getPersonnel(9L)).thenReturn(personnel("产品部"));
        DynamicPersonnelDTO dto = dto("产品部");
        ApiResponse response = controller.update(9L, dto);
        assertEquals(403, response.getCode());
        assertEquals("只能维护所属部门主数据", response.getMessage());
        verifyNoInteractions(approvalService);
    }

    private DynamicPersonnelDTO dto(String department) {
        DynamicPersonnelDTO dto = new DynamicPersonnelDTO();
        dto.ownerDept = department; dto.data = Map.of("employee_code", "E1");
        return dto;
    }

    private MdmPersonnel personnel(String department) {
        MdmPersonnel p = new MdmPersonnel();
        p.setId(9L); p.setSystemCode("HR"); p.setOwnerDept(department);
        p.setDataJson("{}"); p.setStatus("active"); p.setVersion(1);
        return p;
    }
}