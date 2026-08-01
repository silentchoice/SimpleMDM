package com.simplemdm.controller;

import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.system.RecordAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DepartmentControllerTest {
    @AfterEach void clear() { JwtInterceptor.CURRENT_USER.remove(); }

    @Test
    void returnsVisibleDepartmentNamesAndCodes() {
        DepartmentRepository repository = mock(DepartmentRepository.class);
        RecordAccessService access = mock(RecordAccessService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getSystemId()).thenReturn(1L);
        JwtInterceptor.CURRENT_USER.set(user);
        SystemEntity system = SystemEntity.create("HR", "HR");
        ReflectionTestUtils.setField(system, "id", 1L);
        Department department = Department.create(system, null, "HQ", "总部");
        ReflectionTestUtils.setField(department, "id", 10L);
        when(access.readableDepartmentIds(user)).thenReturn(Set.of(10L));
        when(repository.findBySystem_Id(1L)).thenReturn(List.of(department));

        Map<?, ?> node = (Map<?, ?>) ((List<?>) new DepartmentController(repository, access).tree().getData()).get(0);

        assertEquals("HQ", node.get("code"));
        assertEquals("总部", node.get("name"));
    }
}
