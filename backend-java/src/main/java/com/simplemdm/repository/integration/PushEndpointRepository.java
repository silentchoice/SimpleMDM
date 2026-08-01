package com.simplemdm.repository.integration;

import com.simplemdm.model.integration.PushEndpoint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushEndpointRepository extends JpaRepository<PushEndpoint, Long> {
    List<PushEndpoint> findBySystemIdOrderByCode(Long systemId);
    Optional<PushEndpoint> findBySystemIdAndId(Long systemId, Long id);

    @Query("select endpoint.id from PushEndpoint endpoint where endpoint.status='active' "
        + "and endpoint.scheduleEnabled=true and endpoint.scheduleNextAt<=:now order by endpoint.scheduleNextAt")
    List<Long> findDueScheduleIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PushEndpoint endpoint set endpoint.scheduleLastAt=:claimedAt, "
        + "endpoint.scheduleNextAt=:nextAt where endpoint.id=:id and endpoint.status='active' "
        + "and endpoint.scheduleEnabled=true and endpoint.scheduleNextAt=:expectedNextAt")
    int claimSchedule(@Param("id") Long id, @Param("expectedNextAt") LocalDateTime expectedNextAt,
                      @Param("claimedAt") LocalDateTime claimedAt, @Param("nextAt") LocalDateTime nextAt);
}
