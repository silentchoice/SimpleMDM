package com.simplemdm.repository.integration;

import com.simplemdm.model.integration.PushLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushLogRepository extends JpaRepository<PushLog, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PushLog log set log.status='PENDING', log.retryCount=0, "
        + "log.responseSnapshot=null, log.lastAttemptAt=null, log.lastRetryBy=:actorId, "
        + "log.lastRetryReason=:reason, log.lastRetryAt=:requestedAt "
        + "where log.id=:id and log.systemId=:systemId and log.status='FAILED'")
    int requeueFailed(@Param("id") Long id, @Param("systemId") Long systemId,
                      @Param("actorId") Long actorId, @Param("reason") String reason,
                      @Param("requestedAt") LocalDateTime requestedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PushLog log set log.status='RUNNING', log.retryCount=log.retryCount+1, "
        + "log.lastAttemptAt=:claimedAt where log.id=:id and log.retryCount<:maxAttempts and "
        + "(log.status='PENDING' or log.status='FAILED' or "
        + "(log.status='RUNNING' and (log.lastAttemptAt is null or log.lastAttemptAt<:staleBefore)))")
    int claim(@Param("id") Long id, @Param("maxAttempts") int maxAttempts,
              @Param("claimedAt") LocalDateTime claimedAt,
              @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PushLog log set log.status='SUCCESS', log.responseSnapshot=:response "
        + "where log.id=:id and log.status='RUNNING' and log.retryCount=:attempt")
    int completeSucceeded(@Param("id") Long id, @Param("attempt") int attempt,
                          @Param("response") String response);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PushLog log set log.status='FAILED', log.responseSnapshot=:response "
        + "where log.id=:id and log.status='RUNNING' and log.retryCount=:attempt")
    int completeFailed(@Param("id") Long id, @Param("attempt") int attempt,
                       @Param("response") String response);

    @Query("select log.id from PushLog log where log.retryCount<:maxAttempts and "
        + "(log.status='PENDING' or log.status='FAILED' or "
        + "(log.status='RUNNING' and (log.lastAttemptAt is null or log.lastAttemptAt<:staleBefore))) "
        + "order by log.id")
    List<Long> findDeliveryCandidateIds(@Param("maxAttempts") int maxAttempts,
                                        @Param("staleBefore") LocalDateTime staleBefore,
                                        Pageable pageable);

    List<PushLog> findBySystemIdOrderByIdDesc(Long systemId);

    Optional<PushLog> findBySystemIdAndId(Long systemId, Long id);

    Optional<PushLog> findBySystemIdAndActiveDedupKey(Long systemId, String activeDedupKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PushLog log set log.status='CANCELLED', log.cancelledBy=:actorId, "
        + "log.cancelledAt=:cancelledAt, log.cancellationReason=:reason, log.activeDedupKey=null "
        + "where log.id=:id and log.systemId=:systemId and log.status='PENDING'")
    int cancelPending(@Param("id") Long id, @Param("systemId") Long systemId,
                      @Param("actorId") Long actorId, @Param("reason") String reason,
                      @Param("cancelledAt") LocalDateTime cancelledAt);
}
