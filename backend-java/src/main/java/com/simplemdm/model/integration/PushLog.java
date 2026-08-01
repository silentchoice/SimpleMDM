package com.simplemdm.model.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_push_log")
public class PushLog {
    public enum TriggerType { AUTOMATIC, MANUAL, RETRY, SCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "system_id", nullable = false)
    private Long systemId;
    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;
    @Column(name = "record_id")
    private Long recordId;
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;
    @Column(name = "request_snapshot", columnDefinition = "MEDIUMTEXT")
    private String requestSnapshot;
    @Column(name = "response_snapshot")
    private String responseSnapshot;
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16)
    private TriggerType triggerType;
    @Column(name = "triggered_by")
    private Long triggeredBy;
    @Column(name = "trigger_reason", length = 512)
    private String triggerReason;
    @Column(name = "last_retry_by")
    private Long lastRetryBy;
    @Column(name = "last_retry_reason", length = 512)
    private String lastRetryReason;
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;
    @Column(name = "active_dedup_key", length = 160)
    private String activeDedupKey;
    @Column(name = "cancelled_by")
    private Long cancelledBy;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason", length = 512)
    private String cancellationReason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PushLog() {
    }

    public static PushLog pending(Long systemId, Long subscriptionId, Long recordId,
                                  String eventId, String snapshot) {
        return pending(systemId, subscriptionId, recordId, eventId, snapshot,
            TriggerType.AUTOMATIC, null, null);
    }

    public static PushLog manual(Long systemId, Long subscriptionId, Long recordId,
                                 String eventId, String snapshot, Long actorId, String reason) {
        return pending(systemId, subscriptionId, recordId, eventId, snapshot,
            TriggerType.MANUAL, actorId, reason);
    }

    public static PushLog retry(Long systemId, Long subscriptionId, Long recordId,
                                String eventId, String snapshot, Long actorId, String reason) {
        return pending(systemId, subscriptionId, recordId, eventId, snapshot,
            TriggerType.RETRY, actorId, reason);
    }

    private static PushLog pending(Long systemId, Long subscriptionId, Long recordId,
                                   String eventId, String snapshot, TriggerType triggerType,
                                   Long actorId, String reason) {
        PushLog log = new PushLog();
        log.systemId = systemId;
        log.subscriptionId = subscriptionId;
        log.recordId = recordId;
        log.eventId = eventId;
        log.requestSnapshot = snapshot;
        log.status = "PENDING";
        log.retryCount = 0;
        log.triggerType = triggerType;
        log.triggeredBy = actorId;
        log.triggerReason = reason;
        log.idempotencyKey = java.util.UUID.randomUUID().toString();
        log.activeDedupKey = log.idempotencyKey;
        return log;
    }

    @PrePersist
    void create() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getRecordId() { return recordId; }
    public String getEventId() { return eventId; }
    public String getStatus() { return status; }
    public Integer getRetryCount() { return retryCount; }
    public String getRequestSnapshot() { return requestSnapshot; }
    public String getResponseSnapshot() { return responseSnapshot; }
    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public TriggerType getTriggerType() { return triggerType; }
    public Long getTriggeredBy() { return triggeredBy; }
    public String getTriggerReason() { return triggerReason; }
    public Long getLastRetryBy() { return lastRetryBy; }
    public String getLastRetryReason() { return lastRetryReason; }
    public LocalDateTime getLastRetryAt() { return lastRetryAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getActiveDedupKey() { return activeDedupKey; }
    public Long getCancelledBy() { return cancelledBy; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
}
