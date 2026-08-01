package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.integration.PushSubscription;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private static final String MANUAL_PUSH_PERMISSION = "INTEGRATION_MANUAL_PUSH";

    private final PushEndpointRepository endpoints;
    private final PushSubscriptionRepository subscriptions;
    private final PushLogRepository logs;
    private final ObjectTypeRepository objectTypes;
    private final MdmRecordRepository records;
    private final PushEventService events;
    private final EndpointUrlPolicy endpointUrls;
    private final CredentialEncryptionService credentials;
    private final PushScheduleService schedules;
    private final AuthorizationService auth;
    private final RecordAccessService recordAccess;

    public IntegrationController(PushEndpointRepository endpoints,
                                 PushSubscriptionRepository subscriptions,
                                 PushLogRepository logs,
                                 ObjectTypeRepository objectTypes,
                                 MdmRecordRepository records,
                                 PushEventService events,
                                 EndpointUrlPolicy endpointUrls,
                                 CredentialEncryptionService credentials,
                                 PushScheduleService schedules,
                                 AuthorizationService auth,
                                 RecordAccessService recordAccess) {
        this.endpoints = endpoints;
        this.subscriptions = subscriptions;
        this.logs = logs;
        this.objectTypes = objectTypes;
        this.records = records;
        this.events = events;
        this.endpointUrls = endpointUrls;
        this.credentials = credentials;
        this.schedules = schedules;
        this.auth = auth;
        this.recordAccess = recordAccess;
    }

    @GetMapping("/endpoints")
    public ApiResponse endpoints() {
        User user = viewUser();
        return ApiResponse.ok(endpoints.findBySystemIdOrderByCode(user.getSystemId()).stream()
            .map(EndpointView::of)
            .toList());
    }

    @PostMapping("/endpoints")
    public ApiResponse endpoint(@Valid @RequestBody EndpointBody body) {
        User user = manageUser();
        validateEndpointUrl(body.endpoint_url());
        if (!"NONE".equals(body.authentication_type()) && body.credentials() == null) {
            throw new BusinessException(400, "Endpoint authentication credentials are invalid");
        }
        String encryptedCredentials;
        try {
            encryptedCredentials = credentials.encrypt(body.authentication_type(),
                body.credentials() == null ? Map.of() : body.credentials().values());
        } catch (CredentialEncryptionService.CredentialUnavailableException exception) {
            throw new BusinessException(400, "Endpoint authentication credentials are invalid");
        }
        PushEndpoint endpoint = PushEndpoint.create(
            user.getSystemId(), body.code(), body.name(), body.endpoint_url(), body.authentication_type(),
            encryptedCredentials);
        return ApiResponse.ok(EndpointView.of(endpoints.save(endpoint)));
    }

    @PatchMapping("/endpoints/{id}")
    public ApiResponse updateEndpoint(@PathVariable Long id,
                                      @Valid @RequestBody EndpointUpdateBody body) {
        User user = manageUser();
        PushEndpoint endpoint = endpoints.findBySystemIdAndId(user.getSystemId(), id)
            .filter(value -> "active".equals(value.getStatus()))
            .orElseThrow(() -> new BusinessException(404, "Endpoint not found"));
        validateEndpointUrl(body.endpoint_url());
        String encryptedCredentials = updatedCredentials(endpoint, body);
        endpoint.apply(body.name(), body.endpoint_url(), body.authentication_type(), encryptedCredentials);
        return ApiResponse.ok(EndpointView.of(endpoints.save(endpoint)));
    }

    @PatchMapping("/endpoints/{id}/schedule")
    public ApiResponse updateSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleBody body) {
        User user = require(MANUAL_PUSH_PERMISSION);
        PushEndpoint endpoint = endpoints.findBySystemIdAndId(user.getSystemId(), id)
            .filter(value -> "active".equals(value.getStatus()))
            .orElseThrow(() -> new BusinessException(404, "Endpoint not found"));
        schedules.configure(endpoint, body.enabled(), body.cron(), body.timezone(),
            java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        return ApiResponse.ok(EndpointView.of(endpoints.save(endpoint)));
    }

    private String updatedCredentials(PushEndpoint endpoint, EndpointUpdateBody body) {
        if ("NONE".equals(body.authentication_type())) return null;
        if (body.credentials() == null) {
            if (body.authentication_type().equals(endpoint.getAuthenticationType()) && endpoint.hasCredentials()) {
                return endpoint.getEncryptedCredentials();
            }
            throw new BusinessException(400, "Endpoint authentication credentials are invalid");
        }
        try {
            return credentials.encrypt(body.authentication_type(), body.credentials().values());
        } catch (CredentialEncryptionService.CredentialUnavailableException exception) {
            throw new BusinessException(400, "Endpoint authentication credentials are invalid");
        }
    }

    @GetMapping("/subscriptions")
    public ApiResponse subscriptions() {
        User user = viewUser();
        return ApiResponse.ok(subscriptions.findBySystemIdOrderByIdDesc(user.getSystemId()).stream()
            .map(SubscriptionView::of)
            .toList());
    }

    @PostMapping("/subscriptions")
    public ApiResponse subscription(@Valid @RequestBody SubscriptionBody body) {
        User user = manageUser();
        endpoints.findBySystemIdAndId(user.getSystemId(), body.endpoint_id())
            .orElseThrow(() -> new BusinessException(404, "Endpoint not found"));
        objectTypes.findBySystemIdAndId(user.getSystemId(), body.object_type_id())
            .orElseThrow(() -> new BusinessException(404, "Object type not found"));
        PushSubscription subscription = PushSubscription.active(
            null, user.getSystemId(), body.endpoint_id(), body.object_type_id(), body.event_type());
        return ApiResponse.ok(SubscriptionView.of(subscriptions.save(subscription)));
    }

    @GetMapping("/logs")
    public ApiResponse logs() {
        User user = viewUser();
        List<PushLog> systemLogs = logs.findBySystemIdOrderByIdDesc(user.getSystemId());
        List<Long> recordIds = systemLogs.stream().map(PushLog::getRecordId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, MdmRecord> recordsById = recordIds.isEmpty() ? Map.of()
            : records.findBySystemIdAndIdIn(user.getSystemId(), recordIds).stream()
                .collect(Collectors.toMap(MdmRecord::getId, Function.identity()));
        RecordAccessService.Snapshot snapshot = user.isSystemAdmin() ? null : recordAccess.snapshot(user);
        Map<Long, RecordAccessService.Decision> accessByDepartment = new HashMap<>();
        Map<Long, Boolean> retryByDepartment = new HashMap<>();
        return ApiResponse.ok(systemLogs.stream()
            .filter(log -> user.isSystemAdmin()
                || visibleLog(snapshot, log, recordsById, accessByDepartment))
            .map(log -> LogSummaryView.of(log,
                canRetry(user, log, recordsById, retryByDepartment),
                canCancel(user, log, recordsById, retryByDepartment)))
            .toList());
    }

    private boolean visibleLog(RecordAccessService.Snapshot snapshot, PushLog log,
                               Map<Long, MdmRecord> recordsById,
                               Map<Long, RecordAccessService.Decision> accessByDepartment) {
        MdmRecord record = recordsById.get(log.getRecordId());
        return record != null && accessByDepartment.computeIfAbsent(
            record.getDepartmentId(), snapshot::decision) != RecordAccessService.Decision.DENY;
    }

    private boolean canRetry(User user, PushLog log, Map<Long, MdmRecord> recordsById,
                             Map<Long, Boolean> retryByDepartment) {
        if (!"FAILED".equals(log.getStatus())) return false;
        MdmRecord record = recordsById.get(log.getRecordId());
        if (record == null || !record.isActive()) return false;
        return user.isSystemAdmin() || retryByDepartment.computeIfAbsent(record.getDepartmentId(),
            departmentId -> auth.can(user.getId(), MANUAL_PUSH_PERMISSION, departmentId));
    }

    private boolean canCancel(User user, PushLog log, Map<Long, MdmRecord> recordsById,
                              Map<Long, Boolean> permissionByDepartment) {
        if (!"PENDING".equals(log.getStatus())) return false;
        MdmRecord record = recordsById.get(log.getRecordId());
        if (record == null || !record.isActive()) return false;
        return user.isSystemAdmin() || permissionByDepartment.computeIfAbsent(record.getDepartmentId(),
            departmentId -> auth.can(user.getId(), MANUAL_PUSH_PERMISSION, departmentId));
    }

    @GetMapping("/logs/{id}")
    public ApiResponse logDetail(@PathVariable Long id) {
        User user = currentUser();
        if (!user.isSystemAdmin()) {
            throw new BusinessException(403, "System administrator required");
        }
        PushLog log = logs.findBySystemIdAndId(user.getSystemId(), id)
            .orElseThrow(() -> new BusinessException(404, "Push log not found"));
        return ApiResponse.ok(LogDetailView.of(log));
    }

    @PostMapping("/records/{recordId}/distribute")
    public ApiResponse distribute(@PathVariable Long recordId,
                                  @Valid @RequestBody(required = false) TriggerBody body) {
        User user = manualPushUser(recordId);
        String reason = body == null ? null : body.reason();
        return ApiResponse.ok(Map.of("log_ids", events.enqueueManualSnapshot(recordId, user.getId(), reason)));
    }

    @PostMapping("/logs/{logId}/retry")
    public ApiResponse retry(@PathVariable Long logId,
                             @Valid @RequestBody(required = false) TriggerBody body) {
        User user = currentUser();
        PushLog source = logs.findBySystemIdAndId(user.getSystemId(), logId)
            .orElseThrow(() -> new BusinessException(404, "Push log not found"));
        requireManualPush(user, source.getRecordId());
        String reason = body == null ? null : body.reason();
        return ApiResponse.ok(Map.of("log_id", events.retryFailed(logId, user.getId(), reason)));
    }

    @PostMapping("/logs/{logId}/cancel")
    public ApiResponse cancel(@PathVariable Long logId,
                              @Valid @RequestBody(required = false) TriggerBody body) {
        User user = currentUser();
        PushLog source = logs.findBySystemIdAndId(user.getSystemId(), logId)
            .orElseThrow(() -> new BusinessException(404, "Push log not found"));
        requireManualPush(user, source.getRecordId());
        String reason = body == null ? null : body.reason();
        return ApiResponse.ok(Map.of("log_id", events.cancelPending(logId, user.getId(), reason)));
    }

    private User viewUser() {
        return require("MDM_RECORD_VIEW");
    }

    private User manageUser() {
        return require("MDM_FIELD_MANAGE");
    }

    private User manualPushUser(Long recordId) {
        User user = currentUser();
        requireManualPush(user, recordId);
        return user;
    }

    private void requireManualPush(User user, Long recordId) {
        var record = records.findBySystemIdAndId(user.getSystemId(), recordId)
            .filter(MdmRecord::isActive)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (!user.isSystemAdmin()) {
            if (recordAccess.snapshot(user).decision(record.getDepartmentId()) == RecordAccessService.Decision.DENY) {
                throw new BusinessException(404, "Record not found");
            }
            if (!auth.can(user.getId(), MANUAL_PUSH_PERMISSION, record.getDepartmentId())) {
                throw new BusinessException(403, "Manual distribution permission required");
            }
        }
    }

    private User require(String permission) {
        User user = currentUser();
        if (!user.isSystemAdmin() && !auth.can(user.getId(), permission, user.getDepartmentId())) {
            throw new BusinessException(403, "Permission required");
        }
        return user;
    }

    private User currentUser() {
        User user = JwtInterceptor.CURRENT_USER.get();
        if (user == null) {
            throw new BusinessException(401, "System user required");
        }
        return user;
    }

    private void validateEndpointUrl(String value) {
        try {
            endpointUrls.validate(value);
        } catch (EndpointUrlPolicy.RejectedEndpointException
                 | EndpointUrlPolicy.ResolutionTimeoutException exception) {
            throw new BusinessException(400, "Endpoint URL must be a public HTTP(S) URL");
        }
    }

    public record EndpointBody(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 2048) String endpoint_url,
        @NotBlank @Pattern(regexp = "NONE|BASIC|BEARER|API_KEY") String authentication_type,
        @Valid CredentialBody credentials) {
    }

    public record EndpointUpdateBody(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 2048) String endpoint_url,
        @NotBlank @Pattern(regexp = "NONE|BASIC|BEARER|API_KEY") String authentication_type,
        @Valid CredentialBody credentials) {
    }

    public record CredentialBody(
        @Size(max = 1024) String username,
        @Size(max = 1024) String password,
        @Size(max = 1024) String token,
        @Size(max = 128) String header_name,
        @Size(max = 1024) String value) {
        Map<String, String> values() {
            Map<String, String> values = new HashMap<>();
            if (username != null) values.put("username", username);
            if (password != null) values.put("password", password);
            if (token != null) values.put("token", token);
            if (header_name != null) values.put("header_name", header_name);
            if (value != null) values.put("value", value);
            return values;
        }
    }

    public record SubscriptionBody(
        @NotNull Long endpoint_id,
        @NotNull Long object_type_id,
        @NotBlank @Pattern(regexp = "RECORD_CHANGED|RECORD_CREATED|RECORD_UPDATED|RECORD_DELETED") String event_type) {
    }

    public record TriggerBody(@Size(max = 512) String reason) {
    }

    public record ScheduleBody(
        @NotNull Boolean enabled,
        @Size(max = 128) String cron,
        @Size(max = 64) String timezone) {
    }

    public record EndpointView(
        Long id, String code, String name, String endpoint_url, String authentication_type,
        boolean credentials_configured, String status, boolean schedule_enabled,
        String schedule_cron, String schedule_timezone, java.time.LocalDateTime schedule_next_at,
        java.time.LocalDateTime schedule_last_at) {
        static EndpointView of(PushEndpoint endpoint) {
            return new EndpointView(endpoint.getId(), endpoint.getCode(), endpoint.getName(),
                safeEndpointUrl(endpoint.getEndpointUrl()), endpoint.getAuthenticationType(), endpoint.hasCredentials(),
                endpoint.getStatus(), endpoint.isScheduleEnabled(), endpoint.getScheduleCron(),
                endpoint.getScheduleTimezone(), endpoint.getScheduleNextAt(), endpoint.getScheduleLastAt());
        }

        private static String safeEndpointUrl(String value) {
            if (value == null) return "[redacted]";
            try {
                URI uri = new URI(value);
                String scheme = uri.getScheme();
                String authority = uri.getRawAuthority();
                if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || authority == null || uri.getHost() == null) return "[redacted]";
                int userInfoEnd = authority.lastIndexOf('@');
                if (userInfoEnd < 0) return value;
                int authorityStart = value.indexOf(authority, scheme.length() + 3);
                if (authorityStart < 0) return "[redacted]";
                return value.substring(0, authorityStart)
                    + authority.substring(userInfoEnd + 1)
                    + value.substring(authorityStart + authority.length());
            } catch (URISyntaxException | RuntimeException exception) {
                return "[redacted]";
            }
        }
    }

    public record SubscriptionView(
        Long id, Long endpoint_id, Long object_type_id, String event_type, String status) {
        static SubscriptionView of(PushSubscription subscription) {
            return new SubscriptionView(subscription.getId(), subscription.getEndpointId(),
                subscription.getObjectTypeId(), subscription.getEventType(), subscription.getStatus());
        }
    }

    public record LogSummaryView(
        Long id, Long subscription_id, Long record_id, String event_id, String status, Integer retry_count,
        PushLog.TriggerType trigger_type, Long triggered_by, String trigger_reason,
        Long cancelled_by, java.time.LocalDateTime cancelled_at, String cancellation_reason,
        boolean can_retry, boolean can_cancel) {
        static LogSummaryView of(PushLog log, boolean canRetry, boolean canCancel) {
            return new LogSummaryView(log.getId(), log.getSubscriptionId(), log.getRecordId(),
                log.getEventId(), log.getStatus(), log.getRetryCount(), log.getTriggerType(),
                log.getTriggeredBy(), log.getTriggerReason(), log.getCancelledBy(), log.getCancelledAt(),
                log.getCancellationReason(), canRetry, canCancel);
        }
    }

    public record LogDetailView(
        Long id, Long subscription_id, Long record_id, String event_id, String status,
        Integer retry_count, PushLog.TriggerType trigger_type, Long triggered_by, String trigger_reason,
        java.time.LocalDateTime last_attempt_at, String request_snapshot, String response_snapshot,
        Long cancelled_by, java.time.LocalDateTime cancelled_at, String cancellation_reason) {
        static LogDetailView of(PushLog log) {
            return new LogDetailView(log.getId(), log.getSubscriptionId(), log.getRecordId(),
                log.getEventId(), log.getStatus(), log.getRetryCount(), log.getTriggerType(),
                log.getTriggeredBy(), log.getTriggerReason(), log.getLastAttemptAt(),
                log.getRequestSnapshot(), log.getResponseSnapshot(), log.getCancelledBy(),
                log.getCancelledAt(), log.getCancellationReason());
        }
    }
}
