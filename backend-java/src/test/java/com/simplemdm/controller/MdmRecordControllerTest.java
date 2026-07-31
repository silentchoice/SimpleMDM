package com.simplemdm.controller;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.exception.GlobalExceptionHandler;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.mdm.ChildRecordView;
import com.simplemdm.service.mdm.CreateRecordCommand;
import com.simplemdm.service.mdm.RecordService;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MdmRecordControllerTest {
    private RecordService recordService;
    private ObjectTypeRepository objectTypes;
    private MdmRecordRepository records;
    private FieldDefinitionRepository fields;
    private RecordValueRepository values;
    private ChildTypeRepository childTypes;
    private ChildRecordRepository childRecords;
    private AuthorizationService authorization;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recordService = mock(RecordService.class);
        objectTypes = mock(ObjectTypeRepository.class);
        records = mock(MdmRecordRepository.class);
        fields = mock(FieldDefinitionRepository.class);
        values = mock(RecordValueRepository.class);
        childTypes = mock(ChildTypeRepository.class);
        childRecords = mock(ChildRecordRepository.class);
        authorization = mock(AuthorizationService.class);
        mockSystemUser(7L, 10L);
        ObjectType person = ObjectType.create(10L, "person", "Person");
        ReflectionTestUtils.setField(person, "id", 100L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(recordService.create(any())).willReturn(new RecordView(42L, 10L, 100L, 10L, "EMP001", 0L));
        mockMvc = MockMvcBuilders.standaloneSetup(new MdmRecordController(
            recordService, objectTypes, records, fields, values, childTypes, childRecords,
            mock(ChildFieldDefinitionRepository.class), mock(ChildRecordValueRepository.class), authorization
        )).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        JwtInterceptor.CURRENT_USER.remove();
    }

    @Test
    void createsRecordInJwtSystemAndIgnoresUntrustedSystemField() throws Exception {
        mockMvc.perform(post("/api/mdm/object-types/person/records")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"system_id":999,"department_id":10,"record_code":"EMP001",
                     "data":{"employee_name":"Alice","hire_date":"2026-07-31"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.department_id").value(10))
            .andExpect(jsonPath("$.data.object_type").value("person"))
            .andExpect(jsonPath("$.data.record_code").value("EMP001"))
            .andExpect(jsonPath("$.data.data.employee_name").value("Alice"));

        ArgumentCaptor<CreateRecordCommand> command = ArgumentCaptor.forClass(CreateRecordCommand.class);
        verify(recordService).create(command.capture());
        assertThat(command.getValue().systemId()).isEqualTo(10L);
    }

    @Test
    void propagatesDepartmentAuthorizationDeniedByRecordService() throws Exception {
        given(recordService.create(any())).willThrow(new BusinessException(403, "User is not authorized to edit this department"));

        mockMvc.perform(post("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("""
                    {"department_id":99,"record_code":"EMP002","data":{}}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listsVisibleRecordsWithBatchAssembledSnakeCaseData() throws Exception {
        MdmRecord record = mock(MdmRecord.class);
        given(record.getId()).willReturn(42L);
        given(record.getDepartmentId()).willReturn(10L);
        given(record.getRecordCode()).willReturn("EMP001");
        given(record.getStatus()).willReturn("active");
        given(record.getVersion()).willReturn(2L);
        given(authorization.viewableDepartmentIds(7L)).willReturn(Set.of(10L));
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(10L))).willReturn(List.of(record));
        FieldDefinition employeeName = mock(FieldDefinition.class);
        given(employeeName.getId()).willReturn(300L);
        given(employeeName.getFieldKey()).willReturn("employee_name");
        given(fields.findByObjectTypeId(100L)).willReturn(List.of(employeeName));
        RecordValue value = mock(RecordValue.class);
        given(value.getRecordId()).willReturn(42L);
        given(value.getFieldDefinitionId()).willReturn(300L);
        given(value.typedValue()).willReturn(new TypedValue("Alice", null, null, null, null, null, null, null));
        given(values.findByRecordIdIn(List.of(42L))).willReturn(List.of(value));

        mockMvc.perform(get("/api/mdm/object-types/person/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].department_id").value(10))
            .andExpect(jsonPath("$.data[0].data.employee_name").value("Alice"));
    }

    @Test
    void createsAndUpdatesChildWithinPersistedParentContext() throws Exception {
        MdmRecord parent = parentRecord();
        ChildType childType = childType();
        given(records.findById(42L)).willReturn(Optional.of(parent));
        given(childTypes.findAll()).willReturn(List.of(childType));
        given(recordService.createChild(any())).willReturn(new ChildRecordView(99L, 42L, 200L, 10L, 10L, 0L));
        com.simplemdm.model.mdm.ChildRecord persistedChild = childRecord();
        given(childRecords.findById(99L)).willReturn(Optional.of(persistedChild));
        given(authorization.can(7L, "MDM_RECORD_EDIT", 10L)).willReturn(true);
        given(recordService.updateChild(eq(99L), eq(0L), any())).willReturn(new ChildRecordView(99L, 42L, 200L, 10L, 10L, 1L));

        mockMvc.perform(post("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"data":{"number":"123"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parent_record_id").value(42));
        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":0,"data":{"number":"456"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void rejectsChildPutWhenPersistedParentDepartmentIsNotEditable() throws Exception {
        MdmRecord parent = parentRecord();
        given(records.findById(42L)).willReturn(Optional.of(parent));
        given(authorization.can(7L, "MDM_RECORD_EDIT", 10L)).willReturn(false);

        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":0,"data":{"number":"456"}}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void returnsConflictForChildPutWithStaleVersion() throws Exception {
        MdmRecord parent = parentRecord();
        ChildType childType = childType();
        com.simplemdm.model.mdm.ChildRecord persistedChild = childRecord();
        given(records.findById(42L)).willReturn(Optional.of(parent));
        given(childTypes.findAll()).willReturn(List.of(childType));
        given(childRecords.findById(99L)).willReturn(Optional.of(persistedChild));
        given(authorization.can(7L, "MDM_RECORD_EDIT", 10L)).willReturn(true);
        given(recordService.updateChild(eq(99L), eq(0L), any()))
            .willThrow(new BusinessException(409, "Child record version is stale"));

        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":0,"data":{"number":"456"}}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409));
    }
    private MdmRecord parentRecord() {
        MdmRecord parent = mock(MdmRecord.class);
        given(parent.getSystemId()).willReturn(10L);
        given(parent.getObjectTypeId()).willReturn(100L);
        given(parent.getDepartmentId()).willReturn(10L);
        return parent;
    }

    private ChildType childType() {
        ChildType childType = mock(ChildType.class);
        given(childType.getId()).willReturn(200L);
        given(childType.getSystemId()).willReturn(10L);
        given(childType.getObjectTypeId()).willReturn(100L);
        given(childType.getCode()).willReturn("phone");
        return childType;
    }

    private com.simplemdm.model.mdm.ChildRecord childRecord() {
        com.simplemdm.model.mdm.ChildRecord child = mock(com.simplemdm.model.mdm.ChildRecord.class);
        given(child.getRecordId()).willReturn(42L);
        given(child.getChildTypeId()).willReturn(200L);
        return child;
    }

    private void mockSystemUser(Long id, Long systemId) {
        com.simplemdm.model.system.User user = mock(com.simplemdm.model.system.User.class);
        given(user.getId()).willReturn(id);
        given(user.getSystemId()).willReturn(systemId);
        JwtInterceptor.CURRENT_USER.set(user);
    }
}