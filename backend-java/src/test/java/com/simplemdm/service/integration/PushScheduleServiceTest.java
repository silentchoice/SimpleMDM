package com.simplemdm.service.integration;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.repository.integration.PushEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushScheduleServiceTest {
    private PushEndpointRepository endpoints;
    private PushEventService events;
    private PushScheduleService service;

    @BeforeEach
    void setUp() {
        endpoints = mock(PushEndpointRepository.class);
        events = mock(PushEventService.class);
        service = new PushScheduleService(endpoints, events);
    }

    @Test
    void configuresSixFieldCronUsingTheSelectedTimezone() {
        PushEndpoint endpoint = PushEndpoint.create(10L, "ERP", "ERP", "https://example.com", "NONE");
        LocalDateTime nowUtc = LocalDateTime.of(2026, 8, 1, 1, 0);

        service.configure(endpoint, true, "0 30 9 * * *", "Asia/Shanghai", nowUtc);

        assertThat(endpoint.isScheduleEnabled()).isTrue();
        assertThat(endpoint.getScheduleCron()).isEqualTo("0 30 9 * * *");
        assertThat(endpoint.getScheduleTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(endpoint.getScheduleNextAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 1, 30));
    }

    @Test
    void rejectsInvalidCronAndTimezoneWithoutMutatingEndpoint() {
        PushEndpoint endpoint = PushEndpoint.create(10L, "ERP", "ERP", "https://example.com", "NONE");

        assertThatThrownBy(() -> service.configure(endpoint, true, "30 9 * * *", "Asia/Shanghai",
            LocalDateTime.of(2026, 8, 1, 1, 0)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.configure(endpoint, true, "0 30 9 * * *", "Mars/Base",
            LocalDateTime.of(2026, 8, 1, 1, 0)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(400));
        assertThat(endpoint.isScheduleEnabled()).isFalse();
    }

    @Test
    void dueEndpointIsClaimedBeforeUsingTheSharedQueuePath() {
        PushEndpoint endpoint = PushEndpoint.create(10L, "ERP", "ERP", "https://example.com", "NONE");
        ReflectionTestUtils.setField(endpoint, "id", 81L);
        service.configure(endpoint, true, "0 * * * * *", "UTC",
            LocalDateTime.of(2026, 8, 1, 1, 0));
        LocalDateTime due = endpoint.getScheduleNextAt();
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 1, 1, 5);
        when(endpoints.findDueScheduleIds(eq(now), any(Pageable.class))).thenReturn(List.of(81L));
        when(endpoints.findById(81L)).thenReturn(Optional.of(endpoint));
        when(endpoints.claimSchedule(eq(81L), eq(due), eq(now), eq(LocalDateTime.of(2026, 8, 1, 1, 2))))
            .thenReturn(1);
        when(events.enqueueScheduledEndpoint(10L, 81L)).thenReturn(2);

        assertThat(service.enqueueDue(now, 20)).isEqualTo(2);

        verify(events).enqueueScheduledEndpoint(10L, 81L);
    }
}
