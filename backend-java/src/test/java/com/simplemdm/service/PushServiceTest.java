package com.simplemdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushServiceTest {

    @Test
    void sendsDynamicNestedDataPayload() throws Exception {
        SysPushLogRepository logRepository = mock(SysPushLogRepository.class);
        SysPushApiRepository apiRepository = mock(SysPushApiRepository.class);
        MdmPersonnelRepository personnelRepository = mock(MdmPersonnelRepository.class);
        PersonnelService personnelService = mock(PersonnelService.class);
        PushService service = new PushService(
            logRepository, apiRepository, personnelRepository, personnelService, new ObjectMapper());

        MdmPersonnel personnel = new MdmPersonnel();
        personnel.setId(1L);
        personnel.setSystemCode("HR");
        personnel.setOwnerDept("工程部");
        personnel.setDataJson("{\"employee_code\":\"EMP001\",\"name\":\"张三\"}");
        personnel.setVersion(2);
        when(personnelRepository.findById(1L)).thenReturn(Optional.of(personnel));
        when(personnelService.readData(personnel))
            .thenReturn(Map.of("employee_code", "EMP001", "name", "张三"));

        SysPushApi api = new SysPushApi();
        api.setName("CRM");
        api.setTargetSystem("CRM");
        when(apiRepository.findByStatus("active")).thenReturn(List.of(api));
        when(logRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WfApproval approval = new WfApproval();
        approval.setId(10L);
        approval.setPersonnelId(1L);

        SysPushLog log = service.executePush(approval).get(0);
        Map<?, ?> payload = new ObjectMapper().readValue(log.getRequestBody(), Map.class);

        assertEquals(1, payload.get("id"));
        assertEquals("HR", payload.get("system_code"));
        assertEquals("工程部", payload.get("owner_dept"));
        assertEquals(Map.of("employee_code", "EMP001", "name", "张三"), payload.get("data"));
        assertFalse(payload.containsKey("employee_code"));
    }
}
