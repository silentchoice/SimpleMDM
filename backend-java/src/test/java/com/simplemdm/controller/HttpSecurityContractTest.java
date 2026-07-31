package com.simplemdm.controller;

import com.simplemdm.config.WebMvcConfig;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.security.JwtUtil;
import com.simplemdm.security.PermissionAspect;
import com.simplemdm.security.RequirePerm;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.AuthService;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    HttpSecurityContractTest.PermissionProbeController.class
})
@Import({
    WebMvcConfig.class,
    JwtInterceptor.class,
    JwtUtil.class,
    PermissionAspect.class,
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
    @MockBean FieldDefinitionRepository fields;
    @MockBean ApprovalService approvalService;
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
        given(users.findById(7L)).willReturn(Optional.of(user));
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
    void endpointUrlContractAcceptsHttpAndHttpsButRejectsOtherOrHostlessUris() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        given(endpoints.save(any(PushEndpoint.class))).willAnswer(invocation -> invocation.getArgument(0));

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

        mvc.perform(get("/api/workflow/approvals/5").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changes[0].field_key").value("salary"))
            .andExpect(jsonPath("$.data.changes[0].field_name").value("Salary"))
            .andExpect(jsonPath("$.data.changes[0].data_type").value("DECIMAL"))
            .andExpect(jsonPath("$.data.changes[0].old_value").value(10.50))
            .andExpect(jsonPath("$.data.changes[0].new_value").value(12.75));
    }

    @Test
    void approveUsesAuthenticatedIdentityAndPersistedExpectedVersion() throws Exception {
        given(user.isSystemAdmin()).willReturn(true);
        ApprovalRequest request = approvalRequest(5L, 21L, 13L);
        given(approvals.findBySystemIdAndId(10L, 5L)).willReturn(Optional.of(request));

        mvc.perform(post("/api/workflow/approvals/5/approve")
                .header("Authorization", bearer())
                .contentType(APPLICATION_JSON)
                .content("{\"approver_id\":999,\"expected_version\":999}"))
            .andExpect(status().isOk());

        verify(approvalService).approve(5L, 7L, 13L);
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

    private ApprovalRequest approvalRequest(Long id, Long departmentId, Long expectedVersion) {
        ApprovalRequest request = mock(ApprovalRequest.class);
        given(request.getId()).willReturn(id);
        given(request.getDepartmentId()).willReturn(departmentId);
        given(request.getExpectedVersion()).willReturn(expectedVersion);
        return request;
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
