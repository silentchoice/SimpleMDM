package com.simplemdm.controller;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.exception.GlobalExceptionHandler;
import com.simplemdm.dto.mdm.MasterChildChangeRequest;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.User;
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
import com.simplemdm.service.mdm.RecordProjectionService;
import com.simplemdm.service.mdm.RecordService;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.system.RecordAccessService;
import com.simplemdm.service.workflow.ApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private ChildFieldDefinitionRepository childFields;
    private ChildRecordValueRepository childValues;
    private AuthorizationService authorization;
    private RecordAccessService access;
    private RecordAccessService.Snapshot accessSnapshot;
    private RecordProjectionService projection;
    private ApprovalService approvals;
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
        childFields = mock(ChildFieldDefinitionRepository.class);
        childValues = mock(ChildRecordValueRepository.class);
        authorization = mock(AuthorizationService.class);
        access = mock(RecordAccessService.class);
        accessSnapshot = mock(RecordAccessService.Snapshot.class);
        given(access.snapshot(any())).willReturn(accessSnapshot);
        projection = new RecordProjectionService(fields, values, childRecords, childFields, childValues, access);
        approvals = mock(ApprovalService.class);
        mockSystemUser(7L, 10L);
        ObjectType person = ObjectType.create(10L, "person", "Person");
        ReflectionTestUtils.setField(person, "id", 100L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(objectTypes.findById(100L)).willReturn(Optional.of(person));
        given(recordService.create(any())).willReturn(new RecordView(42L, 10L, 100L, 10L, "EMP001", 0L));
        given(approvals.submit(any(MasterChildChangeRequest.class), eq(7L))).willReturn(500L);
        mockMvc = MockMvcBuilders.standaloneSetup(new MdmRecordController(
            recordService, objectTypes, records, childTypes, childRecords, authorization, access, projection, approvals
        )).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        JwtInterceptor.CURRENT_USER.remove();
    }

    @Test
    void createsPendingApprovalFromJwtActorWithoutWritingEffectiveRecord() throws Exception {
        mockMvc.perform(post("/api/mdm/object-types/person/records")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"operation":"CREATE","object_code":"person","department_id":10,"record_code":"EMP001",
                     "data":{"employee_name":"Alice","hire_date":"2026-07-31"},"children":[]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(500))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        ArgumentCaptor<MasterChildChangeRequest> command = ArgumentCaptor.forClass(MasterChildChangeRequest.class);
        verify(approvals).submit(command.capture(), eq(7L));
        assertThat(command.getValue().operation()).isEqualTo(MasterChildChangeRequest.Operation.CREATE);
        verifyNoInteractions(recordService);
    }

    @Test
    void propagatesDepartmentAuthorizationDeniedByApprovalDraftService() throws Exception {
        given(approvals.submit(any(MasterChildChangeRequest.class), eq(7L)))
            .willThrow(new BusinessException(403, "User is not authorized to edit this department"));

        mockMvc.perform(post("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("""
                    {"operation":"CREATE","object_code":"person","department_id":99,
                     "record_code":"EMP002","data":{},"children":[]}
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
        given(record.isActive()).willReturn(true);
        given(record.getVersion()).willReturn(2L);
        given(accessSnapshot.readableDepartmentIds()).willReturn(Set.of(10L));
        given(accessSnapshot.decision(10L)).willReturn(RecordAccessService.Decision.FULL);
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(10L))).willReturn(List.of(record));
        FieldDefinition employeeName = mock(FieldDefinition.class);
        given(employeeName.getId()).willReturn(300L);
        given(employeeName.getFieldKey()).willReturn("employee_name");
        given(employeeName.getStatus()).willReturn("active");
        given(fields.findByObjectTypeId(100L)).willReturn(List.of(employeeName));
        RecordValue value = mock(RecordValue.class);
        given(value.getRecordId()).willReturn(42L);
        given(value.getFieldDefinitionId()).willReturn(300L);
        given(value.typedValue()).willReturn(new TypedValue("Alice", null, null, null, null, null, null, null));
        given(values.findByRecordIdIn(List.of(42L))).willReturn(List.of(value));
        given(authorization.can(7L, "INTEGRATION_MANUAL_PUSH", 10L)).willReturn(true);

        mockMvc.perform(get("/api/mdm/object-types/person/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].department_id").value(10))
            .andExpect(jsonPath("$.data[0].can_distribute").value(true))
            .andExpect(jsonPath("$.data[0].data.employee_name").value("Alice"));
    }

    @Test
    void inactiveAndDeletedRowsCannotBeDistributedWithoutConsultingAuthorization() throws Exception {
        Department department = mock(Department.class);
        given(department.getId()).willReturn(10L);
        MdmRecord inactive = MdmRecord.create(10L, null, 100L, department, "INACTIVE", 7L);
        ReflectionTestUtils.setField(inactive, "id", 41L);
        ReflectionTestUtils.setField(inactive, "status", "inactive");
        MdmRecord deleted = MdmRecord.create(10L, null, 100L, department, "DELETED", 7L);
        ReflectionTestUtils.setField(deleted, "id", 42L);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        given(accessSnapshot.readableDepartmentIds()).willReturn(Set.of(10L));
        given(accessSnapshot.decision(10L)).willReturn(RecordAccessService.Decision.FULL);
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(10L)))
            .willReturn(List.of(inactive, deleted));
        given(fields.findByObjectTypeId(100L)).willReturn(List.of());
        given(values.findByRecordIdIn(List.of(41L, 42L))).willReturn(List.of());

        mockMvc.perform(get("/api/mdm/object-types/person/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        verifyNoInteractions(authorization);
    }

    @Test
    void activeAdministratorCanDistributeWithoutConsultingAuthorization() throws Exception {
        User admin = mock(User.class);
        given(admin.getId()).willReturn(8L);
        given(admin.getSystemId()).willReturn(10L);
        given(admin.isSystemAdmin()).willReturn(true);
        JwtInterceptor.CURRENT_USER.set(admin);
        MdmRecord record = visibleRecord(43L, "ADMIN");
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(10L)))
            .willReturn(List.of(record));

        mockMvc.perform(get("/api/mdm/object-types/person/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].can_distribute").value(true));

        verifyNoInteractions(authorization);
    }

    @Test
    void activeViewerWithoutPermissionCannotDistribute() throws Exception {
        MdmRecord record = visibleRecord(44L, "VIEWER");
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(10L)))
            .willReturn(List.of(record));
        given(authorization.can(7L, "INTEGRATION_MANUAL_PUSH", 10L)).willReturn(false);

        mockMvc.perform(get("/api/mdm/object-types/person/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].can_distribute").value(false));
    }

    @Test
    void detailReturnsOnlyVisibleActiveRecordProjection() throws Exception {
        MdmRecord record = visibleRecord(44L, "VISIBLE");
        given(records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(10L, 100L, 44L))
            .willReturn(Optional.of(record));

        mockMvc.perform(get("/api/mdm/object-types/person/records/44"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.record_code").value("VISIBLE"));
    }

    @Test
    void listBuildsOneDecisionSnapshotAndReusesItForEveryProjectedRecord() throws Exception {
        MdmRecord ownRecord = mock(MdmRecord.class);
        given(ownRecord.getId()).willReturn(41L);
        given(ownRecord.getDepartmentId()).willReturn(10L);
        given(ownRecord.getRecordCode()).willReturn("OWN");
        given(ownRecord.isActive()).willReturn(true);
        MdmRecord sharedRecord = mock(MdmRecord.class);
        given(sharedRecord.getId()).willReturn(42L);
        given(sharedRecord.getDepartmentId()).willReturn(20L);
        given(sharedRecord.getRecordCode()).willReturn("SHARED");
        given(sharedRecord.isActive()).willReturn(true);
        RecordAccessService.Snapshot snapshot = mock(RecordAccessService.Snapshot.class);
        given(snapshot.readableDepartmentIds()).willReturn(Set.of(10L, 20L));
        given(snapshot.decision(10L)).willReturn(RecordAccessService.Decision.FULL);
        given(snapshot.decision(20L)).willReturn(RecordAccessService.Decision.SHARED);
        given(access.snapshot(any())).willReturn(snapshot);
        given(access.readableDepartmentIds(any())).willReturn(Set.of(10L, 20L));
        given(access.access(any(), eq(10L))).willReturn(RecordAccessService.Decision.FULL);
        given(access.access(any(), eq(20L))).willReturn(RecordAccessService.Decision.SHARED);
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(10L, 20L)))
            .willReturn(List.of(ownRecord, sharedRecord));
        given(fields.findByObjectTypeId(100L)).willReturn(List.of());

        mockMvc.perform(get("/api/mdm/object-types/person/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        verify(access).snapshot(any());
        verify(access, never()).readableDepartmentIds(any());
        verify(access, never()).access(any(), any());
    }

    @Test
    void createsAndUpdatesChildAsPendingChangesWithinPersistedParentContext() throws Exception {
        MdmRecord parent = parentRecord();
        ChildType childType = childType();
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(childType));
        com.simplemdm.model.mdm.ChildRecord persistedChild = childRecord();
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(10L, 42L, 200L, 99L))
            .willReturn(Optional.of(persistedChild));

        mockMvc.perform(post("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"data":{"number":"123"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(500))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":0,"data":{"number":"456"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(500));

        ArgumentCaptor<MasterChildChangeRequest> captor = ArgumentCaptor.forClass(MasterChildChangeRequest.class);
        verify(approvals, org.mockito.Mockito.times(2)).submit(captor.capture(), eq(7L));
        assertThat(captor.getAllValues()).allSatisfy(request ->
            assertThat(request.operation()).isEqualTo(MasterChildChangeRequest.Operation.UPDATE));
        assertThat(captor.getAllValues().get(0).children().get(0).rows().get(0).operation())
            .isEqualTo(MasterChildChangeRequest.ChildOperation.CREATE);
        assertThat(captor.getAllValues().get(1).children().get(0).rows().get(0).operation())
            .isEqualTo(MasterChildChangeRequest.ChildOperation.UPDATE);
        verifyNoInteractions(recordService);
    }

    @Test
    void editorCanSubmitMasterAndChildChangesWhenManualDistributionIsDenied() throws Exception {
        MdmRecord parent = parentRecord();
        ChildType childType = childType();
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(childType));
        given(authorization.can(7L, "INTEGRATION_MANUAL_PUSH", 10L)).willReturn(false);

        mockMvc.perform(put("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("""
                    {"operation":"UPDATE","object_code":"person","record_id":42,"expected_version":0,
                     "record_code":"EMP001","department_id":10,"data":{"employee_name":"Alice"},"children":[]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(post("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"data":{"number":"123"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(approvals, org.mockito.Mockito.times(2)).submit(any(MasterChildChangeRequest.class), eq(7L));
        verify(authorization, never()).can(7L, "INTEGRATION_MANUAL_PUSH", 10L);
    }

    @Test
    void rejectsChildPutWhenPersistedParentDepartmentIsNotEditable() throws Exception {
        MdmRecord parent = parentRecord();
        ChildType childType = childType();
        com.simplemdm.model.mdm.ChildRecord persistedChild = childRecord();
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(childType));
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(10L, 42L, 200L, 99L))
            .willReturn(Optional.of(persistedChild));
        given(approvals.submit(any(MasterChildChangeRequest.class), eq(7L))).willThrow(new BusinessException(403, "Applicant cannot edit this department"));

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
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(childType));
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(10L, 42L, 200L, 99L))
            .willReturn(Optional.of(persistedChild));
        given(approvals.submit(any(MasterChildChangeRequest.class), eq(7L)))
            .willThrow(new BusinessException(409, "Child record version is stale"));

        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":0,"data":{"number":"456"}}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409));
    }
    @Test
    void rejectsMissingOrNullVersionAndMalformedJsonAsBadRequest() throws Exception {
        mockMvc.perform(put("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("""
                    {"id":42,"data":{}}
                    """))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("""
                    {"id":42,"version":null,"data":{}}
                    """))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void hidesRecordWhenMainPutRouteObjectTypeDoesNotMatchPersistedRecord() throws Exception {
        given(approvals.submit(any(MasterChildChangeRequest.class), eq(7L))).willThrow(new BusinessException(404, "Record not found"));
        mockMvc.perform(put("/api/mdm/object-types/person/records").contentType(APPLICATION_JSON)
                .content("""
                    {"operation":"UPDATE","object_code":"person","record_id":42,"expected_version":0,
                     "department_id":10,"data":{},"children":[]}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }
    @Test
    void returnsNotFoundForCrossSystemParentOnEveryChildRoute() throws Exception {
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/mdm/records/42/children/phone")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"data":{}}
                    """))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":0,"data":{}}
                    """))
            .andExpect(status().isNotFound());
        verifyNoInteractions(authorization, recordService);
    }

    @Test
    void deniedParentCannotBeUsedToProbeChildTypeCodes() throws Exception {
        MdmRecord parent = parentRecord();
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(accessSnapshot.decision(10L)).willReturn(RecordAccessService.Decision.DENY);

        mockMvc.perform(get("/api/mdm/records/42/children/not-a-real-type"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Record not found"));

        verifyNoInteractions(childTypes);
    }

    @Test
    void batchAssemblesMultipleChildRecordsWithTypedDataAndVersions() throws Exception {
        MdmRecord parent = parentRecord();
        ChildType type = childType();
        com.simplemdm.model.mdm.ChildRecord first = mock(com.simplemdm.model.mdm.ChildRecord.class);
        com.simplemdm.model.mdm.ChildRecord second = mock(com.simplemdm.model.mdm.ChildRecord.class);
        given(first.getId()).willReturn(91L); given(first.getVersion()).willReturn(2L);
        given(second.getId()).willReturn(92L); given(second.getVersion()).willReturn(3L);
        given(first.getStatus()).willReturn("active"); given(second.getStatus()).willReturn("active");
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(accessSnapshot.decision(10L)).willReturn(RecordAccessService.Decision.FULL);
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(type));
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeId(10L, 42L, 200L)).willReturn(List.of(first, second));
        com.simplemdm.model.mdm.ChildFieldDefinition number = mock(com.simplemdm.model.mdm.ChildFieldDefinition.class);
        given(number.getId()).willReturn(300L); given(number.getFieldKey()).willReturn("number");
        given(number.getStatus()).willReturn("active");
        given(childFields.findByChildTypeId(200L)).willReturn(List.of(number));
        com.simplemdm.model.mdm.ChildRecordValue firstValue = mock(com.simplemdm.model.mdm.ChildRecordValue.class);
        com.simplemdm.model.mdm.ChildRecordValue secondValue = mock(com.simplemdm.model.mdm.ChildRecordValue.class);
        given(firstValue.getChildRecordId()).willReturn(91L); given(firstValue.getFieldDefinitionId()).willReturn(300L);
        given(secondValue.getChildRecordId()).willReturn(92L); given(secondValue.getFieldDefinitionId()).willReturn(300L);
        given(firstValue.typedValue()).willReturn(new TypedValue("111", null, null, null, null, null, null, null));
        given(secondValue.typedValue()).willReturn(new TypedValue("222", null, null, null, null, null, null, null));
        given(childValues.findByChildRecordIdInAndFieldDefinitionIdIn(List.of(91L, 92L), List.of(300L)))
            .willReturn(List.of(firstValue, secondValue));
        mockMvc.perform(get("/api/mdm/records/42/children/phone")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].version").value(2)).andExpect(jsonPath("$.data[0].data.number").value("111")).andExpect(jsonPath("$.data[1].version").value(3)).andExpect(jsonPath("$.data[1].data.number").value("222"));
        verify(childRecords).findBySystemIdAndRecordIdAndChildTypeId(10L, 42L, 200L);
        verify(childValues).findByChildRecordIdInAndFieldDefinitionIdIn(List.of(91L, 92L), List.of(300L));
        verify(childRecords, never()).findAll();
    }
    @Test
    void crossDepartmentChildProjectionLoadsAndSerializesOnlySharedValues() throws Exception {
        MdmRecord parent = parentRecord();
        given(parent.getDepartmentId()).willReturn(20L);
        ChildType type = childType();
        com.simplemdm.model.mdm.ChildRecord child = mock(com.simplemdm.model.mdm.ChildRecord.class);
        given(child.getId()).willReturn(91L);
        given(child.getVersion()).willReturn(2L);
        given(child.getStatus()).willReturn("active");
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(accessSnapshot.decision(20L)).willReturn(RecordAccessService.Decision.SHARED);
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(type));
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeId(10L, 42L, 200L)).willReturn(List.of(child));
        com.simplemdm.model.mdm.ChildFieldDefinition shared = mock(com.simplemdm.model.mdm.ChildFieldDefinition.class);
        given(shared.getId()).willReturn(300L);
        given(shared.getFieldKey()).willReturn("public_number");
        given(shared.isShared()).willReturn(true);
        given(shared.getStatus()).willReturn("active");
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of(shared));
        com.simplemdm.model.mdm.ChildRecordValue sharedValue = mock(com.simplemdm.model.mdm.ChildRecordValue.class);
        given(sharedValue.getChildRecordId()).willReturn(91L);
        given(sharedValue.getFieldDefinitionId()).willReturn(300L);
        given(sharedValue.typedValue()).willReturn(new TypedValue("111", null, null, null, null, null, null, null));
        given(childValues.findByChildRecordIdInAndFieldDefinitionIdIn(List.of(91L), List.of(300L)))
            .willReturn(List.of(sharedValue));

        mockMvc.perform(get("/api/mdm/records/42/children/phone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].data.public_number").value("111"))
            .andExpect(jsonPath("$.data[0].data.private_salary").doesNotExist());

        verify(childFields).findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active");
        verify(childValues).findByChildRecordIdInAndFieldDefinitionIdIn(List.of(91L), List.of(300L));
        verify(childValues, never()).findByChildRecordIdIn(any());
    }

    @Test
    void sharedProjectionWithNoSharedFieldsHidesChildExistenceWithoutQueryingRowsOrValues() throws Exception {
        MdmRecord parent = parentRecord();
        given(parent.getDepartmentId()).willReturn(20L);
        ChildType type = childType();
        com.simplemdm.model.mdm.ChildRecord privateChild = mock(com.simplemdm.model.mdm.ChildRecord.class);
        given(privateChild.getId()).willReturn(91L);
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(accessSnapshot.decision(20L)).willReturn(RecordAccessService.Decision.SHARED);
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone")).willReturn(Optional.of(type));
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of());
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeId(10L, 42L, 200L))
            .willReturn(List.of(privateChild));

        mockMvc.perform(get("/api/mdm/records/42/children/phone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        verify(childFields).findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active");
        verifyNoInteractions(childValues);
        verify(childRecords, never()).findBySystemIdAndRecordIdAndChildTypeId(any(), any(), any());
    }

    @Test
    void sharedProjectionHidesInactiveSharedFieldsAndChildExistence() throws Exception {
        MdmRecord parent = parentRecord();
        given(parent.getDepartmentId()).willReturn(20L);
        ChildType type = childType();
        com.simplemdm.model.mdm.ChildFieldDefinition inactive =
            mock(com.simplemdm.model.mdm.ChildFieldDefinition.class);
        given(inactive.getId()).willReturn(300L);
        given(inactive.getFieldKey()).willReturn("former_public_number");
        given(inactive.isShared()).willReturn(true);
        given(inactive.getStatus()).willReturn("inactive");
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(accessSnapshot.decision(20L)).willReturn(RecordAccessService.Decision.SHARED);
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone"))
            .willReturn(Optional.of(type));
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of(inactive));

        mockMvc.perform(get("/api/mdm/records/42/children/phone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        verify(childRecords, never()).findBySystemIdAndRecordIdAndChildTypeId(any(), any(), any());
        verifyNoInteractions(childValues);
    }

    @Test
    void childProjectionHidesInactiveRowsEvenWhenRepositoryReturnsThem() throws Exception {
        MdmRecord parent = parentRecord();
        given(parent.getDepartmentId()).willReturn(20L);
        ChildType type = childType();
        com.simplemdm.model.mdm.ChildFieldDefinition shared =
            mock(com.simplemdm.model.mdm.ChildFieldDefinition.class);
        given(shared.getId()).willReturn(300L);
        given(shared.getFieldKey()).willReturn("public_number");
        given(shared.getStatus()).willReturn("active");
        com.simplemdm.model.mdm.ChildRecord active = mock(com.simplemdm.model.mdm.ChildRecord.class);
        given(active.getId()).willReturn(91L);
        given(active.getStatus()).willReturn("active");
        com.simplemdm.model.mdm.ChildRecord inactive = mock(com.simplemdm.model.mdm.ChildRecord.class);
        given(inactive.getId()).willReturn(92L);
        given(inactive.getStatus()).willReturn("inactive");
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(accessSnapshot.decision(20L)).willReturn(RecordAccessService.Decision.SHARED);
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone"))
            .willReturn(Optional.of(type));
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of(shared));
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeId(10L, 42L, 200L))
            .willReturn(List.of(active, inactive));

        mockMvc.perform(get("/api/mdm/records/42/children/phone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(91));
    }

    @Test
    void rejectsChildPutWhenVersionIsMissingOrNull() throws Exception {
        MdmRecord parent = parentRecord();
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"data":{}}
                    """))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/mdm/records/42/children/phone").contentType(APPLICATION_JSON)
                .content("""
                    {"id":99,"version":null,"data":{}}
                    """))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(authorization, recordService);
    }
    private MdmRecord parentRecord() {
        MdmRecord parent = mock(MdmRecord.class);
        given(parent.getId()).willReturn(42L);
        given(parent.getSystemId()).willReturn(10L);
        given(parent.getObjectTypeId()).willReturn(100L);
        given(parent.getDepartmentId()).willReturn(10L);
        given(parent.isActive()).willReturn(true);
        return parent;
    }

    private ChildType childType() {
        ChildType childType = mock(ChildType.class);
        given(childType.getId()).willReturn(200L);
        given(childType.getSystemId()).willReturn(10L);
        given(childType.getObjectTypeId()).willReturn(100L);
        given(childType.getCode()).willReturn("phone");
        given(childType.getStatus()).willReturn("active");
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

    private MdmRecord visibleRecord(Long id, String code) {
        MdmRecord record = mock(MdmRecord.class);
        given(record.getId()).willReturn(id);
        given(record.getDepartmentId()).willReturn(10L);
        given(record.getRecordCode()).willReturn(code);
        given(record.getStatus()).willReturn("active");
        given(record.isActive()).willReturn(true);
        given(accessSnapshot.readableDepartmentIds()).willReturn(Set.of(10L));
        given(accessSnapshot.decision(10L)).willReturn(RecordAccessService.Decision.FULL);
        given(fields.findByObjectTypeId(100L)).willReturn(List.of());
        given(values.findByRecordIdIn(List.of(id))).willReturn(List.of());
        return record;
    }
}
