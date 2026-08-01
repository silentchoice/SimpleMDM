package com.simplemdm.controller;

import com.simplemdm.exception.GlobalExceptionHandler;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.integration.PushEventService;
import com.simplemdm.service.integration.PushScheduleService;
import com.simplemdm.service.integration.EndpointUrlPolicy;
import com.simplemdm.service.integration.CredentialEncryptionService;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.system.RecordAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntegrationControllerTest {
    private PushEndpointRepository endpoints;
    private PushLogRepository logs;
    private ObjectTypeRepository objects;
    private MdmRecordRepository records;
    private PushEventService events;
    private PushScheduleService schedules;
    private EndpointUrlPolicy endpointUrls;
    private CredentialEncryptionService credentials;
    private AuthorizationService authorization;
    private RecordAccessService recordAccess;
    private RecordAccessService.Snapshot accessSnapshot;
    private User user;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        endpoints = mock(PushEndpointRepository.class);
        logs = mock(PushLogRepository.class);
        objects = mock(ObjectTypeRepository.class);
        records = mock(MdmRecordRepository.class);
        events = mock(PushEventService.class);
        schedules = mock(PushScheduleService.class);
        endpointUrls = mock(EndpointUrlPolicy.class);
        credentials = new CredentialEncryptionService(new com.fasterxml.jackson.databind.ObjectMapper(),
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        authorization = mock(AuthorizationService.class);
        recordAccess = mock(RecordAccessService.class);
        accessSnapshot = mock(RecordAccessService.Snapshot.class);
        user = mock(User.class);
        given(user.getId()).willReturn(7L);
        given(user.getSystemId()).willReturn(10L);
        given(user.isSystemAdmin()).willReturn(true);
        given(recordAccess.snapshot(user)).willReturn(accessSnapshot);
        JwtInterceptor.CURRENT_USER.set(user);
        mvc = MockMvcBuilders.standaloneSetup(new IntegrationController(endpoints,
                mock(PushSubscriptionRepository.class), logs, objects, records, events,
                endpointUrls, credentials, schedules, authorization, recordAccess))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clean() {
        JwtInterceptor.CURRENT_USER.remove();
    }

    @Test
    void rejectsMissingAndMalformedEndpointFields() throws Exception {
        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("{\"code\":\"\",\"name\":\"x\",\"endpoint_url\":\"not-url\",\"authentication_type\":\"NONE\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(endpoints);
    }

    @Test
    void rejectsUrlWithoutHttpHost() throws Exception {
        given(endpointUrls.validate("http:///relative-looking-path"))
            .willThrow(new EndpointUrlPolicy.RejectedEndpointException());
        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("{\"code\":\"x\",\"name\":\"x\",\"endpoint_url\":\"http:///relative-looking-path\",\"authentication_type\":\"NONE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEndpointWhenBoundedDnsValidationTimesOut() throws Exception {
        given(endpointUrls.validate("https://slow.example/hook"))
            .willThrow(new EndpointUrlPolicy.ResolutionTimeoutException());

        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("{\"code\":\"x\",\"name\":\"x\",\"endpoint_url\":\"https://slow.example/hook\",\"authentication_type\":\"NONE\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpoints);
    }

    @Test
    void rejectsUserInfoAndMissingAuthenticationCredentialsBeforePersistingEndpoint() throws Exception {
        given(endpointUrls.validate("https://user:pass@example.com/hook"))
            .willThrow(new EndpointUrlPolicy.RejectedEndpointException());
        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("{\"code\":\"x\",\"name\":\"x\",\"endpoint_url\":\"https://user:pass@example.com/hook\",\"authentication_type\":\"NONE\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("{\"code\":\"x\",\"name\":\"x\",\"endpoint_url\":\"https://example.com/hook\",\"authentication_type\":\"BEARER\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpoints);
    }

    @Test
    void storesAuthenticatedEndpointCredentialsEncryptedWithoutReturningThem() throws Exception {
        String username = "test-endpoint-user";
        String password = "test-endpoint-password";
        given(endpoints.save(any(PushEndpoint.class))).willAnswer(invocation -> invocation.getArgument(0));

        String response = mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("""
                    {"code":"ERP","name":"ERP","endpoint_url":"https://example.com/hook",
                     "authentication_type":"BASIC","credentials":{"username":"test-endpoint-user","password":"test-endpoint-password"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.authentication_type").value("BASIC"))
            .andExpect(jsonPath("$.data.credentials_configured").value(true))
            .andExpect(jsonPath("$.data.credentials").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        org.mockito.ArgumentCaptor<PushEndpoint> endpoint = org.mockito.ArgumentCaptor.forClass(PushEndpoint.class);
        verify(endpoints).save(endpoint.capture());
        assertThat(endpoint.getValue().getEncryptedCredentials())
            .startsWith("v1.")
            .doesNotContain(username, password);
        assertThat(response).doesNotContain(username, password, "encrypted_credentials");
    }

    @Test
    void updatesEndpointConfigurationWithoutReturningOrErasingStoredCredentials() throws Exception {
        PushEndpoint existing = PushEndpoint.create(10L, "ERP", "Old ERP",
            "https://old.example/hook", "BEARER", "v1.existing-encrypted-value");
        ReflectionTestUtils.setField(existing, "id", 41L);
        given(endpoints.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(existing));
        given(endpoints.save(any(PushEndpoint.class))).willAnswer(invocation -> invocation.getArgument(0));

        String response = mvc.perform(patch("/api/integration/endpoints/41").contentType(APPLICATION_JSON)
                .content("""
                    {"name":"New ERP","endpoint_url":"https://new.example/hook",
                     "authentication_type":"BEARER"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("New ERP"))
            .andExpect(jsonPath("$.data.endpoint_url").value("https://new.example/hook"))
            .andExpect(jsonPath("$.data.credentials_configured").value(true))
            .andReturn().getResponse().getContentAsString();

        assertThat(existing.getEncryptedCredentials()).isEqualTo("v1.existing-encrypted-value");
        assertThat(response).doesNotContain("existing-encrypted-value", "encrypted_credentials");
        verify(endpoints).save(existing);
    }

    @Test
    void configuresEndpointScheduleWithManualPushPermissionAndNoCredentialExposure() throws Exception {
        PushEndpoint existing = PushEndpoint.create(10L, "ERP", "ERP",
            "https://example.com/hook", "BEARER", "v1.secret");
        ReflectionTestUtils.setField(existing, "id", 41L);
        given(endpoints.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(existing));
        given(endpoints.save(existing)).willReturn(existing);
        org.mockito.Mockito.doAnswer(invocation -> {
            existing.applySchedule(true, invocation.getArgument(2), invocation.getArgument(3),
                java.time.LocalDateTime.of(2026, 8, 1, 1, 30));
            return null;
        }).when(schedules).configure(eq(existing), eq(true), any(), any(), any());

        String response = mvc.perform(patch("/api/integration/endpoints/41/schedule")
                .contentType(APPLICATION_JSON)
                .content("{\"enabled\":true,\"cron\":\"0 30 9 * * *\",\"timezone\":\"Asia/Shanghai\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.schedule_enabled").value(true))
            .andReturn().getResponse().getContentAsString();

        verify(schedules).configure(eq(existing), eq(true), eq("0 30 9 * * *"),
            eq("Asia/Shanghai"), any(java.time.LocalDateTime.class));
        assertThat(response).doesNotContain("v1.secret", "encrypted_credentials");
    }

    @Test
    void rejectsBasicUsernameContainingColonBeforePersistingEndpoint() throws Exception {
        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("""
                    {"code":"ERP","name":"ERP","endpoint_url":"https://example.com/hook",
                     "authentication_type":"BASIC","credentials":{"username":"test:user","password":"test-password"}}
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpoints);
    }

    @Test
    void rejectsNonAsciiCredentialsWhoseCiphertextExceedsColumnLimitBeforePersistingEndpoint() throws Exception {
        String boundaryValue = "🙂".repeat(512);

        mvc.perform(post("/api/integration/endpoints").contentType(APPLICATION_JSON)
                .content("""
                    {"code":"ERP","name":"ERP","endpoint_url":"https://example.com/hook",
                     "authentication_type":"BASIC","credentials":{"username":"%s","password":"%s"}}
                    """.formatted(boundaryValue, boundaryValue)))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpoints);
    }

    @Test
    void endpointViewNeverSerializesStoredCredentialsOrLegacyUrlUserInfo() throws Exception {
        PushEndpoint endpoint = PushEndpoint.create(
            10L, "erp", "ERP", "https://legacy-user:legacy-secret@public.example/hook", "NONE");
        PushEndpoint malformed = PushEndpoint.create(
            10L, "bad", "Bad", "not-a-url@another-legacy-secret", "NONE");
        ReflectionTestUtils.setField(endpoint, "encryptedCredentials", "credential-fixture-value");
        given(endpoints.findBySystemIdOrderByCode(10L)).willReturn(List.of(endpoint, malformed));

        String response = mvc.perform(get("/api/integration/endpoints"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].endpoint_url").value("https://public.example/hook"))
            .andExpect(jsonPath("$.data[0].encrypted_credentials").doesNotExist())
            .andExpect(jsonPath("$.data[1].endpoint_url").value("[redacted]"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(
            "credential-fixture-value", "legacy-user", "legacy-secret", "another-legacy-secret", "@");
    }

    @Test
    void listsEveryAuthenticationTypeWithoutReturningDemoCredentialPlaceholders() throws Exception {
        PushEndpoint none = PushEndpoint.create(10L, "DEMO_HTTPBIN_NONE", "None",
            "https://httpbin.org/post", "NONE");
        PushEndpoint basic = PushEndpoint.create(10L, "DEMO_HTTPBIN_BASIC", "Basic",
            "https://httpbin.org/post", "BASIC", "v1.local-demo-basic-placeholder");
        PushEndpoint bearer = PushEndpoint.create(10L, "DEMO_HTTPBIN_BEARER", "Bearer",
            "https://httpbin.org/post", "BEARER", "v1.local-demo-bearer-placeholder");
        PushEndpoint apiKey = PushEndpoint.create(10L, "DEMO_HTTPBIN_API_KEY", "API Key",
            "https://httpbin.org/post", "API_KEY", "v1.local-demo-api-key-placeholder");
        given(endpoints.findBySystemIdOrderByCode(10L)).willReturn(List.of(none, basic, bearer, apiKey));

        String response = mvc.perform(get("/api/integration/endpoints"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].credentials").doesNotExist())
            .andExpect(jsonPath("$.data[1].credentials").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("NONE", "BASIC", "BEARER", "API_KEY")
            .doesNotContain("local-demo-", "encrypted_credentials");
    }

    @Test
    void rejectsInvalidSubscriptionAndCrossSystemObjectType() throws Exception {
        mvc.perform(post("/api/integration/subscriptions").contentType(APPLICATION_JSON)
                .content("{\"endpoint_id\":1,\"object_type_id\":2,\"event_type\":\"EVIL\"}"))
            .andExpect(status().isBadRequest());
        given(endpoints.findBySystemIdAndId(10L, 1L)).willReturn(Optional.of(mock()));
        mvc.perform(post("/api/integration/subscriptions").contentType(APPLICATION_JSON)
                .content("{\"endpoint_id\":1,\"object_type_id\":2,\"event_type\":\"RECORD_CHANGED\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void manualDistributionIgnoresClaimedActorAndSystemAndUsesJwtIdentity() throws Exception {
        MdmRecord record = mock(MdmRecord.class);
        given(record.getStatus()).willReturn("active");
        given(record.isActive()).willReturn(true);
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(record));
        given(events.enqueueManualSnapshot(41L, 7L, "send current state")).willReturn(List.of(501L));

        mvc.perform(post("/api/integration/records/41/distribute")
                .contentType(APPLICATION_JSON)
                .content("{\"actor_id\":999,\"system_id\":999,\"reason\":\"send current state\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.log_ids[0]").value(501));

        verify(records).findBySystemIdAndId(10L, 41L);
        verify(events).enqueueManualSnapshot(41L, 7L, "send current state");
    }

    @Test
    void retryUsesJwtIdentityAndRejectsReasonBeyondAuditBound() throws Exception {
        PushLog failed = mock(PushLog.class);
        MdmRecord record = mock(MdmRecord.class);
        given(failed.getRecordId()).willReturn(41L);
        given(record.getStatus()).willReturn("active");
        given(record.isActive()).willReturn(true);
        given(logs.findBySystemIdAndId(10L, 51L)).willReturn(Optional.of(failed));
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(record));
        given(events.retryFailed(51L, 7L, "retry now")).willReturn(601L);

        mvc.perform(post("/api/integration/logs/51/retry")
                .contentType(APPLICATION_JSON).content("{\"reason\":\"retry now\",\"actor_id\":999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.log_id").value(601));
        mvc.perform(post("/api/integration/logs/51/retry")
                .contentType(APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(513) + "\"}"))
            .andExpect(status().isBadRequest());

        verify(events).retryFailed(51L, 7L, "retry now");
    }

    @Test
    void cancelUsesTenantScopedLogRecordPermissionAndJwtIdentity() throws Exception {
        PushLog pending = mock(PushLog.class);
        MdmRecord record = mock(MdmRecord.class);
        given(pending.getRecordId()).willReturn(41L);
        given(pending.getStatus()).willReturn("PENDING");
        given(record.isActive()).willReturn(true);
        given(logs.findBySystemIdAndId(10L, 51L)).willReturn(Optional.of(pending));
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(record));
        given(events.cancelPending(51L, 7L, "计划已变更")).willReturn(51L);

        mvc.perform(post("/api/integration/logs/51/cancel")
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"计划已变更\",\"actor_id\":999,\"system_id\":999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.log_id").value(51));

        verify(logs).findBySystemIdAndId(10L, 51L);
        verify(records).findBySystemIdAndId(10L, 41L);
        verify(events).cancelPending(51L, 7L, "计划已变更");
    }

    @Test
    void crossTenantCancellationReturnsNotFoundBeforeCallingQueueService() throws Exception {
        given(logs.findBySystemIdAndId(10L, 51L)).willReturn(Optional.empty());

        mvc.perform(post("/api/integration/logs/51/cancel")
                .contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(events);
    }

    @Test
    void deniedRecordIsHiddenBeforeManualPushPermissionIsChecked() throws Exception {
        given(user.isSystemAdmin()).willReturn(false);
        MdmRecord record = mock(MdmRecord.class);
        given(record.isActive()).willReturn(true);
        given(record.getDepartmentId()).willReturn(22L);
        given(records.findBySystemIdAndId(10L, 41L)).willReturn(Optional.of(record));
        given(accessSnapshot.decision(22L)).willReturn(RecordAccessService.Decision.DENY);

        mvc.perform(post("/api/integration/records/41/distribute")
                .contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());

        verify(authorization, never()).can(7L, "INTEGRATION_MANUAL_PUSH", 22L);
        verifyNoInteractions(events);
    }

    @Test
    void logSummaryProjectsRetryCapabilityWithoutSnapshots() throws Exception {
        PushLog failed = mock(PushLog.class);
        MdmRecord record = mock(MdmRecord.class);
        given(failed.getId()).willReturn(51L);
        given(failed.getRecordId()).willReturn(41L);
        given(failed.getStatus()).willReturn("FAILED");
        given(record.getStatus()).willReturn("active");
        given(record.isActive()).willReturn(true);
        given(record.getDepartmentId()).willReturn(21L);
        given(record.getId()).willReturn(41L);
        given(logs.findBySystemIdOrderByIdDesc(10L)).willReturn(List.of(failed));
        given(records.findBySystemIdAndIdIn(10L, List.of(41L))).willReturn(List.of(record));

        mvc.perform(get("/api/integration/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].can_retry").value(true))
            .andExpect(jsonPath("$.data[0].request_snapshot").doesNotExist());
    }

    @Test
    void logSummaryHidesDeniedAndMissingRecordAuditMetadata() throws Exception {
        given(user.isSystemAdmin()).willReturn(false);
        given(user.getDepartmentId()).willReturn(21L);
        given(authorization.can(7L, "MDM_RECORD_VIEW", 21L)).willReturn(true);
        PushLog allowed = log(51L, 41L, "allowed-event", 7L, "allowed reason");
        PushLog denied = log(52L, 42L, "denied-event", 99L, "denied secret reason");
        PushLog missing = log(53L, 404L, "missing-event", 98L, "missing secret reason");
        MdmRecord allowedRecord = mock(MdmRecord.class);
        MdmRecord deniedRecord = mock(MdmRecord.class);
        given(allowedRecord.getId()).willReturn(41L);
        given(allowedRecord.getDepartmentId()).willReturn(21L);
        given(deniedRecord.getId()).willReturn(42L);
        given(deniedRecord.getDepartmentId()).willReturn(22L);
        given(logs.findBySystemIdOrderByIdDesc(10L)).willReturn(List.of(allowed, denied, missing));
        given(records.findBySystemIdAndIdIn(10L, List.of(41L, 42L, 404L)))
            .willReturn(List.of(allowedRecord, deniedRecord));
        given(accessSnapshot.decision(21L)).willReturn(RecordAccessService.Decision.FULL);
        given(accessSnapshot.decision(22L)).willReturn(RecordAccessService.Decision.DENY);

        String response = mvc.perform(get("/api/integration/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].event_id").value("allowed-event"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(
            "denied-event", "denied secret reason", "missing-event", "missing secret reason", "99", "98");
    }

    @Test
    void logSummaryBatchesRecordsAndReusesAccessAndRetryDecisions() throws Exception {
        given(user.isSystemAdmin()).willReturn(false);
        given(user.getDepartmentId()).willReturn(21L);
        given(authorization.can(7L, "MDM_RECORD_VIEW", 21L)).willReturn(true);
        given(authorization.can(7L, "INTEGRATION_MANUAL_PUSH", 21L)).willReturn(true);
        PushLog firstFailed = log(51L, 41L, "first-failed", 7L, null);
        PushLog secondFailed = log(52L, 42L, "second-failed", 7L, null);
        PushLog pending = log(53L, 42L, "pending", 7L, null);
        PushLog missing = log(54L, 404L, "missing", 7L, null);
        PushLog withoutRecord = log(55L, null, "without-record", 7L, null);
        given(firstFailed.getStatus()).willReturn("FAILED");
        given(secondFailed.getStatus()).willReturn("FAILED");
        MdmRecord firstRecord = mock(MdmRecord.class);
        MdmRecord secondRecord = mock(MdmRecord.class);
        given(firstRecord.getId()).willReturn(41L);
        given(firstRecord.getDepartmentId()).willReturn(21L);
        given(firstRecord.isActive()).willReturn(true);
        given(secondRecord.getId()).willReturn(42L);
        given(secondRecord.getDepartmentId()).willReturn(21L);
        given(secondRecord.isActive()).willReturn(true);
        given(logs.findBySystemIdOrderByIdDesc(10L)).willReturn(
            List.of(firstFailed, secondFailed, pending, missing, withoutRecord));
        given(records.findBySystemIdAndIdIn(10L, List.of(41L, 42L, 404L)))
            .willReturn(List.of(firstRecord, secondRecord));
        given(accessSnapshot.decision(21L)).willReturn(RecordAccessService.Decision.FULL);

        mvc.perform(get("/api/integration/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].can_retry").value(true))
            .andExpect(jsonPath("$.data[1].can_retry").value(true))
            .andExpect(jsonPath("$.data[2].can_retry").value(false));

        verify(records).findBySystemIdAndIdIn(10L, List.of(41L, 42L, 404L));
        verify(records, never()).findBySystemIdAndId(any(), any());
        verify(recordAccess).snapshot(user);
        verify(accessSnapshot, times(1)).decision(21L);
        verify(authorization, times(1)).can(7L, "INTEGRATION_MANUAL_PUSH", 21L);
    }

    private PushLog log(Long id, Long recordId, String eventId, Long actorId, String reason) {
        PushLog log = mock(PushLog.class);
        given(log.getId()).willReturn(id);
        given(log.getRecordId()).willReturn(recordId);
        given(log.getEventId()).willReturn(eventId);
        given(log.getStatus()).willReturn("PENDING");
        given(log.getTriggeredBy()).willReturn(actorId);
        given(log.getTriggerReason()).willReturn(reason);
        return log;
    }
}
