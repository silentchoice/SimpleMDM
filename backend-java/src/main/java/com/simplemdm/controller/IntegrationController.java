package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.integration.PushSubscription;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.system.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private final PushEndpointRepository endpoints;
    private final PushSubscriptionRepository subscriptions;
    private final PushLogRepository logs;
    private final ObjectTypeRepository objectTypes;
    private final AuthorizationService auth;

    public IntegrationController(PushEndpointRepository endpoints,
                                 PushSubscriptionRepository subscriptions,
                                 PushLogRepository logs,
                                 ObjectTypeRepository objectTypes,
                                 AuthorizationService auth) {
        this.endpoints = endpoints;
        this.subscriptions = subscriptions;
        this.logs = logs;
        this.objectTypes = objectTypes;
        this.auth = auth;
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
        PushEndpoint endpoint = PushEndpoint.create(
            user.getSystemId(), body.code(), body.name(), body.endpoint_url(), body.authentication_type());
        return ApiResponse.ok(EndpointView.of(endpoints.save(endpoint)));
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
        return ApiResponse.ok(logs.findBySystemIdOrderByIdDesc(user.getSystemId()).stream()
            .map(LogSummaryView::of)
            .toList());
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

    private User viewUser() {
        return require("MDM_RECORD_VIEW");
    }

    private User manageUser() {
        return require("MDM_FIELD_MANAGE");
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
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException(400, "Endpoint URL must be an absolute HTTP(S) URL");
            }
        } catch (URISyntaxException exception) {
            throw new BusinessException(400, "Endpoint URL must be an absolute HTTP(S) URL");
        }
    }

    public record EndpointBody(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotBlank String endpoint_url,
        @NotBlank @Pattern(regexp = "NONE|BASIC|BEARER") String authentication_type) {
    }

    public record SubscriptionBody(
        @NotNull Long endpoint_id,
        @NotNull Long object_type_id,
        @NotBlank @Pattern(regexp = "RECORD_CHANGED|RECORD_CREATED|RECORD_UPDATED|RECORD_DELETED") String event_type) {
    }

    public record EndpointView(
        Long id, String code, String name, String endpoint_url, String authentication_type, String status) {
        static EndpointView of(PushEndpoint endpoint) {
            return new EndpointView(endpoint.getId(), endpoint.getCode(), endpoint.getName(),
                endpoint.getEndpointUrl(), endpoint.getAuthenticationType(), endpoint.getStatus());
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
        Long id, Long subscription_id, Long record_id, String event_id, String status, Integer retry_count) {
        static LogSummaryView of(PushLog log) {
            return new LogSummaryView(log.getId(), log.getSubscriptionId(), log.getRecordId(),
                log.getEventId(), log.getStatus(), log.getRetryCount());
        }
    }

    public record LogDetailView(
        Long id, Long subscription_id, Long record_id, String event_id, String status,
        Integer retry_count, String request_snapshot) {
        static LogDetailView of(PushLog log) {
            return new LogDetailView(log.getId(), log.getSubscriptionId(), log.getRecordId(),
                log.getEventId(), log.getStatus(), log.getRetryCount(), log.getRequestSnapshot());
        }
    }
}
