package com.simplemdm.repository.integration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Locale;

@Repository
public class PushLogOutboxWriter {
    private static final String DEDUP_CONSTRAINT = "uk_push_log_active_dedup";

    private final JdbcTemplate jdbc;

    public PushLogOutboxWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int insertAutomatic(Long systemId, Long subscriptionId, Long recordId,
                               String eventId, String snapshot) {
        return insertAutomatic(systemId, subscriptionId, recordId, eventId, snapshot,
            systemId + ":" + subscriptionId + ":" + eventId);
    }

    public int insertAutomatic(Long systemId, Long subscriptionId, Long recordId,
                               String eventId, String snapshot, String activeDedupKey) {
        return insert(systemId, subscriptionId, recordId, eventId, snapshot, "AUTOMATIC",
            null, null, activeDedupKey);
    }

    public int insertManual(Long systemId, Long subscriptionId, Long recordId,
                            String eventId, String snapshot, Long actorId, String reason,
                            String activeDedupKey) {
        return insert(systemId, subscriptionId, recordId, eventId, snapshot, "MANUAL",
            actorId, reason, activeDedupKey);
    }

    public int insertScheduled(Long systemId, Long subscriptionId, Long recordId,
                               String eventId, String snapshot, String activeDedupKey) {
        return insert(systemId, subscriptionId, recordId, eventId, snapshot, "SCHEDULED",
            null, null, activeDedupKey);
    }

    private int insert(Long systemId, Long subscriptionId, Long recordId,
                       String eventId, String snapshot, String triggerType,
                       Long actorId, String reason, String activeDedupKey) {
        try {
            int inserted = jdbc.update("""
                INSERT INTO sys_push_log
                    (system_id, subscription_id, record_id, event_id, status, retry_count,
                     request_snapshot, trigger_type, triggered_by, trigger_reason,
                     idempotency_key, active_dedup_key, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, systemId, subscriptionId, recordId, eventId, snapshot,
                triggerType, actorId, reason, java.util.UUID.randomUUID().toString(), activeDedupKey);
            if (inserted != 1) {
                throw new IllegalStateException("Push queue insert did not create exactly one row");
            }
            return inserted;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateDeduplicationKey(exception)) return 0;
            throw exception;
        }
    }

    private boolean isDuplicateDeduplicationKey(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(DEDUP_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
