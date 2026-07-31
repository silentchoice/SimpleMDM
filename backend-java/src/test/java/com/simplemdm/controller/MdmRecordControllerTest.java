package com.simplemdm.controller;

import com.simplemdm.dto.mdm.RecordResponse;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.mdm.RecordService;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MdmRecordControllerTest {
    private RecordService records;
    private ObjectTypeRepository objectTypes;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        records = mock(RecordService.class);
        objectTypes = mock(ObjectTypeRepository.class);
        mockSystemUser(7L, 10L);
        ObjectType person = ObjectType.create(10L, "person", "Person");
        ReflectionTestUtils.setField(person, "id", 100L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(records.create(any())).willReturn(new RecordView(42L, 10L, 100L, 10L, "EMP001", 0L));
        mockMvc = MockMvcBuilders.standaloneSetup(new MdmRecordController(
            records, objectTypes, mock(MdmRecordRepository.class), mock(FieldDefinitionRepository.class),
            mock(RecordValueRepository.class), mock(ChildTypeRepository.class), mock(ChildRecordRepository.class),
            mock(ChildFieldDefinitionRepository.class), mock(ChildRecordValueRepository.class), mock(AuthorizationService.class)
        )).build();
    }

    @AfterEach
    void tearDown() {
        JwtInterceptor.CURRENT_USER.remove();
    }

    @Test
    void createsRecordInJwtSystemAndReturnsSnakeCaseData() throws Exception {
        mockMvc.perform(post("/api/mdm/object-types/person/records")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"department_id":10,"record_code":"EMP001",
                     "data":{"employee_name":"Alice","hire_date":"2026-07-31"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.department_id").value(10))
            .andExpect(jsonPath("$.data.object_type").value("person"))
            .andExpect(jsonPath("$.data.data.employee_name").value("Alice"));
    }

    private void mockSystemUser(Long id, Long systemId) {
        com.simplemdm.model.system.User user = mock(com.simplemdm.model.system.User.class);
        given(user.getId()).willReturn(id);
        given(user.getSystemId()).willReturn(systemId);
        JwtInterceptor.CURRENT_USER.set(user);
    }
}
