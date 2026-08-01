package com.simplemdm.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.integration.PushSubscription;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PushDeliveryService {
    public enum Outcome { SUCCEEDED, FAILED, SKIPPED }

    public record DeliveryResult(Long logId, Outcome outcome, Integer httpStatus, Integer attempt) {
    }

    private final PushLogRepository logs;
    private final PushSubscriptionRepository subscriptions;
    private final PushEndpointRepository endpoints;
    private final ObjectMapper json;
    private final EndpointUrlPolicy endpointUrls;
    private final PinnedHttpTransport transport;
    private final CredentialEncryptionService credentials;
    private final TransactionTemplate transactions;
    private final int maxAttempts;
    private final int responseLimit;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final Duration claimTimeout;

    public PushDeliveryService(PushLogRepository logs, PushSubscriptionRepository subscriptions,
                               PushEndpointRepository endpoints, ObjectMapper json,
                               EndpointUrlPolicy endpointUrls, PinnedHttpTransport transport,
                               CredentialEncryptionService credentials,
                               PlatformTransactionManager transactionManager,
                               @Value("${simple-mdm.push.max-attempts:3}") int maxAttempts,
                               @Value("${simple-mdm.push.response-snapshot-limit:2000}") int responseLimit,
                               @Value("${simple-mdm.push.connect-timeout-ms:3000}") int connectTimeoutMs,
                               @Value("${simple-mdm.push.request-timeout-ms:10000}") int requestTimeoutMs,
                               @Value("${simple-mdm.push.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.logs = logs;
        this.subscriptions = subscriptions;
        this.endpoints = endpoints;
        this.json = json;
        this.endpointUrls = endpointUrls;
        this.transport = transport;
        this.credentials = credentials;
        this.maxAttempts = Math.max(1, Math.min(20, maxAttempts));
        this.responseLimit = Math.max(128, Math.min(4096, responseLimit));
        this.connectTimeout = Duration.ofMillis(Math.max(100, Math.min(30_000, connectTimeoutMs)));
        this.requestTimeout = Duration.ofMillis(Math.max(100, Math.min(60_000, requestTimeoutMs)));
        this.claimTimeout = Duration.ofSeconds(Math.max(10, Math.min(3600, claimTimeoutSeconds)));
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public DeliveryResult deliver(Long logId) {
        Optional<Claim> claimed = claim(logId);
        if (claimed.isEmpty()) return new DeliveryResult(logId, Outcome.SKIPPED, null, null);
        Claim claim = claimed.get();
        AttemptResult attempt = attempt(claim);
        int completed = transactions.execute(status -> attempt.success()
            ? logs.completeSucceeded(claim.logId(), claim.attempt(), attempt.responseSnapshot())
            : logs.completeFailed(claim.logId(), claim.attempt(), attempt.responseSnapshot()));
        if (completed != 1) {
            return new DeliveryResult(logId, Outcome.SKIPPED, attempt.httpStatus(), claim.attempt());
        }
        return new DeliveryResult(logId, attempt.success() ? Outcome.SUCCEEDED : Outcome.FAILED,
            attempt.httpStatus(), claim.attempt());
    }

    public List<Long> deliveryCandidates(int batchSize) {
        int boundedBatch = Math.max(1, Math.min(100, batchSize));
        LocalDateTime staleBefore = LocalDateTime.now().minus(claimTimeout);
        return transactions.execute(status -> logs.findDeliveryCandidateIds(maxAttempts, staleBefore,
            PageRequest.of(0, boundedBatch)));
    }

    private Optional<Claim> claim(Long logId) {
        if (logId == null) return Optional.empty();
        return transactions.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            int updated = logs.claim(logId, maxAttempts, now, now.minus(claimTimeout));
            if (updated != 1) return Optional.empty();
            PushLog log = logs.findById(logId).orElseThrow();
            return Optional.of(new Claim(log.getId(), log.getSystemId(), log.getSubscriptionId(),
                log.getEventId(), log.getRequestSnapshot(), log.getRetryCount()));
        });
    }

    private AttemptResult attempt(Claim claim) {
        AttemptDeadline deadline = AttemptDeadline.after(requestTimeout);
        PushSubscription subscription = subscriptions.findById(claim.subscriptionId()).orElse(null);
        if (subscription == null || !claim.systemId().equals(subscription.getSystemId())
            || !"active".equals(subscription.getStatus())) {
            return failed("SUBSCRIPTION_UNAVAILABLE");
        }
        PushEndpoint endpoint = endpoints.findBySystemIdAndId(claim.systemId(), subscription.getEndpointId())
            .filter(value -> "active".equals(value.getStatus())).orElse(null);
        if (endpoint == null) return failed("ENDPOINT_UNAVAILABLE");
        Map<String, String> authenticationHeaders;
        try {
            authenticationHeaders = credentials.requestHeaders(endpoint.getAuthenticationType(),
                endpoint.getEncryptedCredentials());
        } catch (CredentialEncryptionService.CredentialUnavailableException exception) {
            return failed("AUTHENTICATION_UNAVAILABLE");
        }
        EndpointUrlPolicy.ValidatedEndpoint validated;
        try {
            validated = endpointUrls.validate(endpoint.getEndpointUrl(), deadline);
        } catch (EndpointUrlPolicy.ResolutionTimeoutException exception) {
            return failed("ATTEMPT_TIMEOUT");
        } catch (EndpointUrlPolicy.RejectedEndpointException exception) {
            return failed("ENDPOINT_CONFIGURATION_ERROR");
        }
        try {
            if (claim.snapshot() == null || claim.eventId() == null || claim.eventId().isBlank()
                || claim.eventId().indexOf('\r') >= 0 || claim.eventId().indexOf('\n') >= 0) {
                return failed("REQUEST_CONFIGURATION_ERROR");
            }
            PinnedHttpTransport.Response response = transport.postJson(validated, claim.eventId(),
                claim.snapshot(), responseLimit * 4 + 1, connectTimeout, deadline, authenticationHeaders);
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new AttemptResult(success, response.statusCode(), boundedResponse(
                response.statusCode(), response.body(), response.truncated(), authenticationHeaders.isEmpty()));
        } catch (PinnedHttpTransport.AttemptTimeoutException exception) {
            return failed("ATTEMPT_TIMEOUT");
        } catch (IOException | RuntimeException exception) {
            return failed("IO_ERROR");
        }
    }

    private AttemptResult failed(String errorCode) {
        return new AttemptResult(false, null, boundedError(errorCode));
    }

    private String boundedResponse(int httpStatus, String body, boolean inputTruncated, boolean includeBody) {
        if (!includeBody) {
            return write(Map.of("http_status", httpStatus, "body_omitted", true));
        }
        String safeBody = body == null ? "" : body;
        int codePoints = safeBody.codePointCount(0, safeBody.length());
        for (int kept = Math.min(codePoints, responseLimit); kept >= 0; kept--) {
            int end = safeBody.offsetByCodePoints(0, kept);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("http_status", httpStatus);
            value.put("body", safeBody.substring(0, end));
            value.put("truncated", inputTruncated || kept < codePoints);
            String serialized = write(value);
            if (serialized.length() <= responseLimit) return serialized;
        }
        return boundedError("RESPONSE_METADATA_LIMIT");
    }

    private String boundedError(String errorCode) {
        return write(Map.of("error", errorCode));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize push response metadata", exception);
        }
    }

    private record Claim(Long logId, Long systemId, Long subscriptionId, String eventId,
                         String snapshot, int attempt) {
    }

    private record AttemptResult(boolean success, Integer httpStatus, String responseSnapshot) {
    }
}
