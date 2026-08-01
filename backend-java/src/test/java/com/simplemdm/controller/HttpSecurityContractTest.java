package com.simplemdm.controller;

import com.simplemdm.config.WebMvcConfig;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalChildChange;
import com.simplemdm.model.workflow.ApprovalChildValueChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.security.JwtUtil;
import com.simplemdm.security.PermissionAspect;
import com.simplemdm.security.RequirePerm;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.system.RecordAccessService;
import com.simplemdm.service.AuthService;
import com.simplemdm.service.mdm.RecordProjectionService;
import com.simplemdm.service.mdm.RecordService;
import com.simplemdm.service.integration.PushEventService;
import com.simplemdm.service.integration.PushScheduleService;
import com.simplemdm.service.integration.EndpointUrlPolicy;
import com.simplemdm.service.integration.CredentialEncryptionService;
import com.simplemdm.service.workflow.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
    IntegrationController.class,
    WorkflowController.class,
    AuthController.class,
    UserController.class,
    MdmRecordController.class,
    HttpSecurityContractTest.PermissionProbeController.class
})
@Import({
    WebMvcConfig.class,
    JwtInterceptor.class,
    JwtUtil.class,
    PermissionAspect.class,
    RecordAccessService.class,
    RecordProjectionService.class,
    HttpSecurityContractTest.PermissionProbeController.class,
    HttpSecurityContractTest.AopTestConfiguration.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-minutes=60"
})
class HttpSecurityContractTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean UserRepository users;
    @MockBean PushEndpointRepository endpoints;
    @MockBean PushSubscriptionRepository subscriptions;
    @MockBean PushLogRepository logs;
    @MockBean ObjectTypeRepository objectTypes;
    @MockBean ApprovalRequestRepository approvals;
    @MockBean ApprovalChangeRepository changes;
    @MockBean ApprovalChildChangeRepository childChanges;
    @MockBean ApprovalChildValueChangeRepository childValueChanges;
    @MockBean FieldDefinitionRepository fields;
    @MockBean DepartmentRepository departments;
    @MockBean MdmRecordRepository records;
    @MockBean RecordValueRepository values;
    @MockBean ChildTypeRepository childTypes;
    @MockBean ChildRecordRepository childRecords;
    @MockBean ChildFieldDefinitionRepository childFields;
    @MockBean ChildRecordValueRepository childValues;
    @MockBean RecordService recordService;
    @MockBean ApprovalService approvalService;
    @MockBean PushEventService pushEvents;
    @MockBean PushScheduleService pushSchedules;
    @MockBean EndpointUrlPolicy endpointUrls;
    @MockBean CredentialEncryptionService credentials;
    @MockBean AuthService authService;
    @MockBean AuthorizationService authorization;

    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        given(user.getId()).willReturn(7L);
        given(user.getSystemId()).willReturn(10L);
        given(user.getDepartmentId()).willReturn(21L);
        given(user.isActive()).willReturn(true);
        given(user.isSystemActive()).willReturn(true);
        given(users.findWithContextById(7L)).willReturn(Optional.of(user));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(true, false, Set.of(21L)));
    }

    @Test
    void rejectsUnauthenticatedWorkflowAndIntegrationThroughConfiguredInterceptor() throws Exception {
        mvc.perform(get("/api/workflow/approvals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/integration/endpoints")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginUsesRealHttp401Status() throws Exception {
        given(authService.login("ERP", "operator", "wrong"))
            .willThrow(new IllegalArgumentException("Invalid username or password"));

        mvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"system_code\":\"ERP\",\"username\":\"operator\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void userListQueriesOnlyAuthenticatedSystem() throws Exception {
        given(user.getUsername()).willReturn("tenant-ten-user");
        given(user.getRealName()).willReturn("Tenant Ten User");
        given(user.getStatus()).willReturn("active");
        given(users.findBySystemIdAndStatusOrderById(10L, "active")).willReturn(List.of(user));

        mvc.perform(get("/api/users").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].system_id").value(10))
            .andExpect(jsonPath("$.data[0].username").value("tenant-ten-user"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("other-tenant"))));

        verify(users).findBySystemIdAndStatusOrderById(10L, "active");
    }

    @Test
    void rejectsAuthenticatedUserWithoutRequiredPermission() throws Exception {
        given(user.isSystemAdmin()).willReturn(false);
        given(authorization.can(anyLong(), anyString(), any())).willReturn(false);

        mvc.perform(post("/api/integration/endpoints")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"endpoint_url\":\"https://erp.example\",\"authentication_type\":\"NONE\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void permissionAspectEnforcesAnnotatedHttpEndpointForAuthenticatedUser() throws Exception {
        given(user.isSystemAdmin()).willReturn(false);
        given(authorization.can(7L, "MDM_RECORD_VIEW", 21L)).willReturn(false, true);

        mvc.perform(get("/api/test-security/21").header("Authorization", bearer()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/test-security/21").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(content().string("allowed"));

        verify(authorization, times(2)).can(7L, "MDM_RECORD_VIEW", 21L);
    }

    @Test
    void mdmReadContractUsesJwtAccessDecisionAndReturnsAllMasterFieldsAcrossDepartments() throws Exception {
        ObjectType person = mock(ObjectType.class);
        given(person.getId()).willReturn(100L);
        given(person.isActive()).willReturn(true);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(true, true, Set.of(21L)));

        MdmRecord ownRecord = record(41L, 21L, "OWN");
        MdmRecord otherRecord = record(42L, 22L, "OTHER");
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(21L, 22L)))
            .willReturn(List.of(ownRecord, otherRecord));
        FieldDefinition privateMaster = mock(FieldDefinition.class);
        given(privateMaster.getId()).willReturn(301L);
        given(privateMaster.getFieldKey()).willReturn("private_master");
        given(privateMaster.isShared()).willReturn(false);
        given(privateMaster.getStatus()).willReturn("active");
        given(fields.findByObjectTypeId(100L)).willReturn(List.of(privateMaster));
        RecordValue ownValue = value(41L, 301L, "own-secret");
        RecordValue otherValue = value(42L, 301L, "other-secret");
        given(values.findByRecordIdIn(List.of(41L, 42L))).willReturn(List.of(ownValue, otherValue));

        mvc.perform(get("/api/mdm/object-types/person/records").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].data.private_master").value("own-secret"))
            .andExpect(jsonPath("$.data[1].data.private_master").value("other-secret"));
    }

    @Test
    void crossDepartmentChildHttpContractNeverQueriesOrReturnsPrivateValues() throws Exception {
        MdmRecord parent = record(42L, 22L, "OTHER");
        given(parent.getObjectTypeId()).willReturn(100L);
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(false, true, Set.of()));
        ChildType childType = mock(ChildType.class);
        given(childType.getId()).willReturn(200L);
        given(childType.getStatus()).willReturn("active");
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "phone"))
            .willReturn(Optional.of(childType));
        ChildRecord child = mock(ChildRecord.class);
        given(child.getId()).willReturn(91L);
        given(child.getVersion()).willReturn(2L);
        given(child.getStatus()).willReturn("active");
        given(childRecords.findBySystemIdAndRecordIdAndChildTypeId(10L, 42L, 200L))
            .willReturn(List.of(child));
        ChildFieldDefinition shared = mock(ChildFieldDefinition.class);
        given(shared.getId()).willReturn(300L);
        given(shared.getFieldKey()).willReturn("public_number");
        given(shared.isShared()).willReturn(true);
        given(shared.getStatus()).willReturn("active");
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of(shared));
        ChildRecordValue sharedValue = mock(ChildRecordValue.class);
        given(sharedValue.getChildRecordId()).willReturn(91L);
        given(sharedValue.getFieldDefinitionId()).willReturn(300L);
        given(sharedValue.typedValue()).willReturn(new TypedValue("111", null, null, null, null, null, null, null));
        given(childValues.findByChildRecordIdInAndFieldDefinitionIdIn(List.of(91L), List.of(300L)))
            .willReturn(List.of(sharedValue));

        mvc.perform(get("/api/mdm/records/42/children/phone").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].data.public_number").value("111"))
            .andExpect(jsonPath("$.data[0].data.private_salary").doesNotExist());

        verify(childValues).findByChildRecordIdInAndFieldDefinitionIdIn(List.of(91L), List.of(300L));
        verify(childValues, never()).findByChildRecordIdIn(any());
    }

    @Test
    void deniedChildReadThroughJwtReturnsNotFoundBeforeChildMetadataOrRowsAreQueried() throws Exception {
        MdmRecord parent = record(42L, 22L, "DENIED");
        given(parent.getObjectTypeId()).willReturn(100L);
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.of(parent));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(false, false, Set.of()));

        mvc.perform(get("/api/mdm/records/42/children/private-type").header("Authorization", bearer()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Record not found"));

        verifyNoInteractions(childTypes, childRecords, childFields, childValues);
    }

    @Test
    void crossSystemChildReadThroughJwtUsesAuthenticatedSystemAndReturnsNotFound() throws Exception {
        given(records.findBySystemIdAndId(10L, 42L)).willReturn(Optional.empty());

        mvc.perform(get("/api/mdm/records/42/children/phone").header("Authorization", bearer()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Record not found"));

        verify(records).findBySystemIdAndId(10L, 42L);
        verifyNoInteractions(departments, authorization, childTypes, childRecords, childFields, childValues);
    }

    @Test
    void systemAdministratorHttpListGetsFullSameSystemProjectionWithoutAuthorizationQueries() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        ObjectType person = mock(ObjectType.class);
        given(person.getId()).willReturn(100L);
        given(person.isActive()).willReturn(true);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        MdmRecord first = record(41L, 21L, "FIRST");
        MdmRecord second = record(42L, 22L, "SECOND");
        given(records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(10L, 100L, Set.of(21L, 22L)))
            .willReturn(List.of(first, second));
        given(fields.findByObjectTypeId(100L)).willReturn(List.of());

        mvc.perform(get("/api/mdm/object-types/person/records").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].record_code").value("FIRST"))
            .andExpect(jsonPath("$.data[1].record_code").value("SECOND"));

        verify(departments).findActiveIdsBySystemId(10L);
        verifyNoInteractions(authorization);
    }

    @Test
    void endpointUrlContractAcceptsHttpAndHttpsButRejectsOtherOrHostlessUris() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        given(endpoints.save(any(PushEndpoint.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(endpointUrls.validate("http://erp.example/hooks")).willReturn(new EndpointUrlPolicy.ValidatedEndpoint(
            URI.create("http://erp.example/hooks"), List.of(InetAddress.getByName("93.184.216.34"))));
        given(endpointUrls.validate("https://erp.example/hooks")).willReturn(new EndpointUrlPolicy.ValidatedEndpoint(
            URI.create("https://erp.example/hooks"), List.of(InetAddress.getByName("93.184.216.34"))));
        given(endpointUrls.validate("ftp://erp.example/hooks"))
            .willThrow(new EndpointUrlPolicy.RejectedEndpointException());
        given(endpointUrls.validate("https:///hostless"))
            .willThrow(new EndpointUrlPolicy.RejectedEndpointException());

        postEndpoint("http://erp.example/hooks", 200);
        postEndpoint("https://erp.example/hooks", 200);
        postEndpoint("ftp://erp.example/hooks", 400);
        postEndpoint("https:///hostless", 400);

        verify(endpoints, times(2)).save(any(PushEndpoint.class));
    }

    @Test
    void allListsAreScopedToAuthenticatedSystemAndLogListOmitsSnapshot() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        PushLog log = mock(PushLog.class);
        given(log.getRequestSnapshot()).willReturn("{secret:true}");
        given(logs.findBySystemIdOrderByIdDesc(10L)).willReturn(List.of(log));

        mvc.perform(get("/api/workflow/approvals").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/integration/endpoints").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/integration/subscriptions").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/integration/logs").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("request_snapshot"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));

        verify(approvals).findBySystemIdOrderByIdDesc(10L);
        verify(endpoints).findBySystemIdOrderByCode(10L);
        verify(subscriptions).findBySystemIdOrderByIdDesc(10L);
        verify(logs).findBySystemIdOrderByIdDesc(10L);
    }

    @Test
    void crossSystemApprovalDetailIsNotFound() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        given(approvals.findBySystemIdAndId(10L, 99L)).willReturn(Optional.empty());

        mvc.perform(get("/api/workflow/approvals/99").header("Authorization", bearer()))
            .andExpect(status().isNotFound());

        verify(approvals).findBySystemIdAndId(10L, 99L);
    }

    @Test
    void approvalDetailReturnsRelationalTypedChanges() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        ApprovalRequest request = approvalRequest(5L, 21L, 3L);
        ApprovalChange change = mock(ApprovalChange.class);
        FieldDefinition field = mock(FieldDefinition.class);
        given(change.getFieldDefinitionId()).willReturn(31L);
        given(change.oldValue()).willReturn(new com.simplemdm.model.mdm.TypedValue(null, null, null, new BigDecimal("10.50"), null, null, null, null));
        given(change.newValue()).willReturn(new com.simplemdm.model.mdm.TypedValue(null, null, null, new BigDecimal("12.75"), null, null, null, null));
        given(field.getId()).willReturn(31L);
        given(field.getFieldKey()).willReturn("salary");
        given(field.getFieldName()).willReturn("Salary");
        given(field.getDataType()).willReturn(FieldDataType.DECIMAL);
        given(approvals.findBySystemIdAndId(10L, 5L)).willReturn(Optional.of(request));
        given(changes.findByApprovalRequestId(5L)).willReturn(List.of(change));
        given(fields.findAllById(List.of(31L))).willReturn(List.of(field));
        given(approvalService.canApprove(request, 7L)).willReturn(true);

        mvc.perform(get("/api/workflow/approvals/5").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changes[0].field_key").value("salary"))
            .andExpect(jsonPath("$.data.changes[0].field_name").value("Salary"))
            .andExpect(jsonPath("$.data.changes[0].data_type").value("DECIMAL"))
            .andExpect(jsonPath("$.data.changes[0].old_value").value(10.50))
            .andExpect(jsonPath("$.data.changes[0].new_value").value(12.75))
            .andExpect(jsonPath("$.data.can_approve").value(true));
    }

    @Test
    void approvalListOmitsDeniedDepartmentsUsingRecordAccessSnapshot() throws Exception {
        ApprovalRequest visible = approvalRequest(5L, 21L, 3L);
        ApprovalRequest hidden = approvalRequest(6L, 22L, 3L);
        given(approvals.findBySystemIdOrderByIdDesc(10L)).willReturn(List.of(hidden, visible));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(true, false, Set.of(21L)));

        mvc.perform(get("/api/workflow/approvals").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(5));
    }

    @Test
    void deniedApprovalDetailReturnsUniformNotFoundBeforeChangesAreRead() throws Exception {
        ApprovalRequest hidden = approvalRequest(6L, 22L, 3L);
        given(approvals.findBySystemIdAndId(10L, 6L)).willReturn(Optional.of(hidden));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(true, false, Set.of(21L)));

        mvc.perform(get("/api/workflow/approvals/6").header("Authorization", bearer()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Approval not found"));

        verifyNoInteractions(changes, childChanges, childValueChanges);
    }

    @Test
    void sharedApprovalDetailReturnsOnlySharedChildFields() throws Exception {
        ApprovalRequest request = approvalRequest(6L, 22L, 3L);
        ApprovalChildChange group = mock(ApprovalChildChange.class);
        given(group.getId()).willReturn(81L);
        given(group.getChangeKey()).willReturn("phone:0");
        given(group.getChildTypeId()).willReturn(200L);
        given(group.getOperation()).willReturn(ApprovalChildChange.Operation.UPDATE);
        ApprovalChildValueChange publicChange = childApprovalValue(81L, 301L, "111", "222");
        ApprovalChildValueChange privateChange = childApprovalValue(81L, 302L, "secret", "new-secret");
        ApprovalChildValueChange inactiveChange = childApprovalValue(
            81L, 303L, "former-public", "inactive-secret");
        ChildFieldDefinition shared = childField(301L, "public_number", true);
        given(shared.getFieldName()).willReturn("公开号码");
        ChildFieldDefinition privateField = childField(302L, "private_salary", false);
        ChildFieldDefinition inactiveShared = childField(303L, "former_public_number", true);
        given(inactiveShared.getStatus()).willReturn("inactive");
        given(approvals.findBySystemIdAndId(10L, 6L)).willReturn(Optional.of(request));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(false, true, Set.of()));
        given(childChanges.findByApprovalRequestIdOrderBySortOrderAscIdAsc(6L)).willReturn(List.of(group));
        ChildType phone = mock(ChildType.class);
        given(phone.getId()).willReturn(200L);
        given(phone.getSystemId()).willReturn(10L);
        given(phone.getName()).willReturn("电话信息");
        given(phone.getStatus()).willReturn("active");
        given(childTypes.findAllById(List.of(200L))).willReturn(List.of(phone));
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of(shared, inactiveShared));
        given(childValueChanges.findByApprovalChildChangeIdInAndFieldDefinitionIdIn(
            List.of(81L), Set.of(301L))).willReturn(List.of(publicChange));

        mvc.perform(get("/api/workflow/approvals/6").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.child_changes.length()").value(1))
            .andExpect(jsonPath("$.data.child_changes[0].values.length()").value(1))
            .andExpect(jsonPath("$.data.child_changes[0].values[0].field_key").value("public_number"))
            .andExpect(jsonPath("$.data.child_changes[0].child_type_name").value("电话信息"))
            .andExpect(jsonPath("$.data.child_changes[0].values[0].field_name").value("公开号码"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private_salary"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("new-secret"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("former_public_number"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("inactive-secret"))));
    }

    @Test
    void sharedApprovalDetailHidesChildChangeExistenceWhenTypeHasNoSharedFields() throws Exception {
        ApprovalRequest request = approvalRequest(6L, 22L, 3L);
        ApprovalChildChange group = mock(ApprovalChildChange.class);
        given(group.getId()).willReturn(81L);
        given(group.getChildTypeId()).willReturn(200L);
        given(group.getOperation()).willReturn(ApprovalChildChange.Operation.DELETE);
        ApprovalChildValueChange privateChange = childApprovalValue(81L, 302L, "secret", null);
        ChildFieldDefinition privateField = childField(302L, "private_salary", false);
        given(approvals.findBySystemIdAndId(10L, 6L)).willReturn(Optional.of(request));
        given(departments.findActiveIdsBySystemId(10L)).willReturn(List.of(21L, 22L));
        given(authorization.recordViewAuthorization(7L, 10L)).willReturn(
            new AuthorizationService.RecordViewAuthorization(false, true, Set.of()));
        given(childChanges.findByApprovalRequestIdOrderBySortOrderAscIdAsc(6L)).willReturn(List.of(group));
        given(childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(200L, "active"))
            .willReturn(List.of());

        mvc.perform(get("/api/workflow/approvals/6").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.child_changes.length()").value(0));
    }

    @Test
    void approveUsesAuthenticatedIdentityAndIgnoresClaimedActorAndVersion() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        ApprovalRequest request = approvalRequest(5L, 21L, 13L);
        given(approvals.findBySystemIdAndId(10L, 5L)).willReturn(Optional.of(request));

        mvc.perform(post("/api/workflow/approvals/5/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{\"approver_id\":999,\"expected_version\":999}"))
            .andExpect(status().isOk());

        verify(approvalService).approve(5L, 7L);
    }

    @Test
    void approveDelegatesWithoutRequiringRecordViewPermission() throws Exception {
        given(approvalService.approve(5L, 7L)).willReturn(null);

        mvc.perform(post("/api/workflow/approvals/5/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        verify(approvalService).approve(5L, 7L);
        verifyNoInteractions(approvals);
    }

    @Test
    void unauthorizedJwtActorCannotDistinguishPendingFromApprovedApprovalIds() throws Exception {
        given(approvalService.approve(5L, 7L))
            .willThrow(new BusinessException(404, "Approval request not found"));
        given(approvalService.approve(6L, 7L))
            .willThrow(new BusinessException(404, "Approval request not found"));

        mvc.perform(post("/api/workflow/approvals/5/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Approval request not found"));
        mvc.perform(post("/api/workflow/approvals/6/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Approval request not found"));
    }

    @Test
    void assignedJwtActorsSeePendingSuccessAndAlreadyApprovedConflict() throws Exception {
        given(approvalService.approve(5L, 7L)).willReturn(null);
        given(approvalService.approve(6L, 7L))
            .willThrow(new BusinessException(409, "Approval request is not pending"));

        mvc.perform(post("/api/workflow/approvals/5/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/workflow/approvals/6/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Approval request is not pending"));
    }

    @Test
    void rejectUsesJwtActorMapsInvisibleAndConflictStatesAndValidatesCommentLength() throws Exception {
        mvc.perform(post("/api/workflow/approvals/5/reject")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON)
                .content("{\"comment\":\"  数据不完整  \"}"))
            .andExpect(status().isOk());
        verify(approvalService).reject(5L, 7L, "  数据不完整  ");

        willThrow(new BusinessException(404, "Approval request not found"))
            .given(approvalService).reject(6L, 7L, null);
        willThrow(new BusinessException(409, "Approval request is not pending"))
            .given(approvalService).reject(7L, 7L, null);

        mvc.perform(post("/api/workflow/approvals/6/reject")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Approval request not found"));
        mvc.perform(post("/api/workflow/approvals/7/reject")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Approval request is not pending"));

        mvc.perform(post("/api/workflow/approvals/8/reject")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON)
                .content("{\"comment\":\"" + "x".repeat(2049) + "\"}"))
            .andExpect(status().isBadRequest());
        verify(approvalService, never()).reject(8L, 7L, "x".repeat(2049));
    }

    @Test
    void regularRecordViewerCannotReadSnapshotButSystemAdminCan() throws Exception {
        PushLog log = mock(PushLog.class);
        given(log.getRequestSnapshot()).willReturn("{secret:true}");
        given(logs.findBySystemIdAndId(10L, 5L)).willReturn(Optional.of(log));
        given(user.isSystemAdmin()).willReturn(false);
        given(authorization.can(7L, "MDM_RECORD_VIEW", 21L)).willReturn(true);

        mvc.perform(get("/api/integration/logs/5").header("Authorization", bearer()))
            .andExpect(status().isForbidden());

        given(user.isSystemAdmin()).willReturn(true);
        mvc.perform(get("/api/integration/logs/5").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.request_snapshot").value("{secret:true}"));
    }

    @Test
    void manualDistributionRequiresJwtAndAllowsPermissionBearingEditorOrApprover() throws Exception {
        MdmRecord target = record(41L, 21L, "EMP-41");
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(target));
        given(user.isSystemAdmin()).willReturn(false);
        given(authorization.can(7L, "INTEGRATION_MANUAL_PUSH", 21L)).willReturn(true);
        given(pushEvents.enqueueManualSnapshot(41L, 7L, "editor action")).willReturn(List.of(501L));
        given(pushEvents.enqueueManualSnapshot(41L, 7L, "approver action")).willReturn(List.of(502L));

        mvc.perform(post("/api/integration/records/41/distribute")
                .contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integration/records/41/distribute")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"editor action\",\"actor_id\":999,\"system_id\":999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.log_ids[0]").value(501));
        mvc.perform(post("/api/integration/records/41/distribute")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"approver action\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.log_ids[0]").value(502));

        verify(records, times(2)).findBySystemIdAndId(10L, 41L);
        verify(pushEvents).enqueueManualSnapshot(41L, 7L, "editor action");
        verify(pushEvents).enqueueManualSnapshot(41L, 7L, "approver action");
    }

    @Test
    void viewerAndCrossViewerCannotDistributeOrRetry() throws Exception {
        MdmRecord target = record(41L, 21L, "EMP-41");
        PushLog failed = mock(PushLog.class);
        given(failed.getRecordId()).willReturn(41L);
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(target));
        given(logs.findBySystemIdAndId(10L, 51L)).willReturn(Optional.of(failed));
        given(user.isSystemAdmin()).willReturn(false);
        given(authorization.can(7L, "INTEGRATION_MANUAL_PUSH", 21L)).willReturn(false);

        mvc.perform(post("/api/integration/records/41/distribute")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/integration/logs/51/retry")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(pushEvents);
    }

    @Test
    void currentSystemAdminCanDistributeAndRetryWithoutRolePermission() throws Exception {
        MdmRecord target = record(41L, 21L, "EMP-41");
        PushLog failed = mock(PushLog.class);
        given(failed.getRecordId()).willReturn(41L);
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(target));
        given(logs.findBySystemIdAndId(10L, 51L)).willReturn(Optional.of(failed));
        given(user.isSystemAdmin()).willReturn(true);
        given(pushEvents.enqueueManualSnapshot(41L, 7L, null)).willReturn(List.of(501L));
        given(pushEvents.retryFailed(51L, 7L, null)).willReturn(601L);

        mvc.perform(post("/api/integration/records/41/distribute")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/integration/logs/51/retry")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());

        verifyNoInteractions(authorization);
        verify(pushEvents).enqueueManualSnapshot(41L, 7L, null);
        verify(pushEvents).retryFailed(51L, 7L, null);
    }

    @Test
    void crossSystemManualTargetsAreUniformNotFoundAndNeverEnqueued() throws Exception {
        given(records.findBySystemIdAndId(10L, 404L)).willReturn(Optional.empty());
        given(logs.findBySystemIdAndId(10L, 505L)).willReturn(Optional.empty());
        given(user.isSystemAdmin()).willReturn(true);

        mvc.perform(post("/api/integration/records/404/distribute")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/integration/logs/505/retry")
                .header("Authorization", bearer()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());

        verify(records).findBySystemIdAndId(10L, 404L);
        verify(logs).findBySystemIdAndId(10L, 505L);
        verifyNoInteractions(pushEvents);
    }

    private ApprovalRequest approvalRequest(Long id, Long departmentId, Long expectedVersion) {
        ApprovalRequest request = mock(ApprovalRequest.class);
        given(request.getId()).willReturn(id);
        given(request.getSystemId()).willReturn(10L);
        given(request.getOperation()).willReturn(ApprovalRequest.Operation.UPDATE);
        given(request.getDepartmentId()).willReturn(departmentId);
        given(request.getExpectedVersion()).willReturn(expectedVersion);
        return request;
    }

    private MdmRecord record(Long id, Long departmentId, String code) {
        MdmRecord record = mock(MdmRecord.class);
        given(record.getId()).willReturn(id);
        given(record.getDepartmentId()).willReturn(departmentId);
        given(record.getRecordCode()).willReturn(code);
        given(record.getStatus()).willReturn("active");
        given(record.isActive()).willReturn(true);
        given(record.getVersion()).willReturn(1L);
        return record;
    }

    private RecordValue value(Long recordId, Long fieldId, String content) {
        RecordValue value = mock(RecordValue.class);
        given(value.getRecordId()).willReturn(recordId);
        given(value.getFieldDefinitionId()).willReturn(fieldId);
        given(value.typedValue()).willReturn(new TypedValue(content, null, null, null, null, null, null, null));
        return value;
    }

    private ApprovalChildValueChange childApprovalValue(Long groupId, Long fieldId, String oldValue,
                                                         String newValue) {
        ApprovalChildValueChange change = mock(ApprovalChildValueChange.class);
        given(change.getSystemId()).willReturn(10L);
        given(change.getApprovalChildChangeId()).willReturn(groupId);
        given(change.getFieldDefinitionId()).willReturn(fieldId);
        given(change.oldValue()).willReturn(oldValue == null ? TypedValue.empty()
            : new TypedValue(oldValue, null, null, null, null, null, null, null));
        given(change.newValue()).willReturn(newValue == null ? TypedValue.empty()
            : new TypedValue(newValue, null, null, null, null, null, null, null));
        return change;
    }

    private ChildFieldDefinition childField(Long id, String key, boolean shared) {
        ChildFieldDefinition field = mock(ChildFieldDefinition.class);
        given(field.getId()).willReturn(id);
        given(field.getSystemId()).willReturn(10L);
        given(field.getFieldKey()).willReturn(key);
        given(field.getStatus()).willReturn("active");
        given(field.getDataType()).willReturn(FieldDataType.STRING);
        given(field.isShared()).willReturn(shared);
        return field;
    }

    private String bearer() {
        return "Bearer " + jwt.createToken(7L, 10L);
    }

    private void postEndpoint(String endpointUrl, int expectedStatus) throws Exception {
        mvc.perform(post("/api/integration/endpoints")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"endpoint_url\":\""
                    + endpointUrl + "\",\"authentication_type\":\"NONE\"}"))
            .andExpect(status().is(expectedStatus));
    }

    @RestController
    @RequestMapping("/api/test-security")
    public static class PermissionProbeController {
        @GetMapping("/{departmentId}")
        @RequirePerm(value = "MDM_RECORD_VIEW", departmentArgument = 0)
        String view(@PathVariable Long departmentId) {
            return "allowed";
        }
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class AopTestConfiguration {
    }
}
