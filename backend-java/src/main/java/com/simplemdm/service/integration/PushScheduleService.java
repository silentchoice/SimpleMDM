package com.simplemdm.service.integration;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.repository.integration.PushEndpointRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class PushScheduleService {
    private final PushEndpointRepository endpoints;
    private final PushEventService events;

    public PushScheduleService(PushEndpointRepository endpoints, PushEventService events) {
        this.endpoints = endpoints;
        this.events = events;
    }

    public void configure(PushEndpoint endpoint, boolean enabled, String cron, String timezone,
                          LocalDateTime nowUtc) {
        if (!enabled) {
            endpoint.applySchedule(false, null, null, null);
            return;
        }
        Schedule schedule = validated(cron, timezone);
        endpoint.applySchedule(true, cron.trim(), schedule.zone().getId(), next(schedule, nowUtc));
    }

    @Transactional
    public int enqueueDue(LocalDateTime nowUtc, int batchSize) {
        int queued = 0;
        for (Long endpointId : endpoints.findDueScheduleIds(nowUtc,
            PageRequest.of(0, Math.max(1, Math.min(100, batchSize))))) {
            PushEndpoint endpoint = endpoints.findById(endpointId).orElse(null);
            if (endpoint == null || !endpoint.isScheduleEnabled() || endpoint.getScheduleNextAt() == null) continue;
            Schedule schedule;
            try {
                schedule = validated(endpoint.getScheduleCron(), endpoint.getScheduleTimezone());
            } catch (BusinessException invalidStoredPolicy) {
                continue;
            }
            LocalDateTime expected = endpoint.getScheduleNextAt();
            LocalDateTime next = next(schedule, nowUtc);
            if (endpoints.claimSchedule(endpointId, expected, nowUtc, next) != 1) continue;
            queued += events.enqueueScheduledEndpoint(endpoint.getSystemId(), endpointId);
        }
        return queued;
    }

    private Schedule validated(String cron, String timezone) {
        try {
            if (cron == null || cron.isBlank() || timezone == null || timezone.isBlank()) {
                throw new IllegalArgumentException();
            }
            return new Schedule(CronExpression.parse(cron.trim()), ZoneId.of(timezone.trim()));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new BusinessException(400, "Invalid schedule cron or timezone");
        }
    }

    private LocalDateTime next(Schedule schedule, LocalDateTime nowUtc) {
        ZonedDateTime current = nowUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(schedule.zone());
        ZonedDateTime next = schedule.cron().next(current);
        if (next == null) throw new BusinessException(400, "Schedule has no next execution");
        return next.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private record Schedule(CronExpression cron, ZoneId zone) { }
}
