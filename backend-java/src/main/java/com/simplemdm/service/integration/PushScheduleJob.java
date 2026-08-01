package com.simplemdm.service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PushScheduleJob {
    private final PushScheduleService schedules;
    private final int batchSize;

    public PushScheduleJob(PushScheduleService schedules,
                           @Value("${simple-mdm.push.schedule-batch-size:20}") int batchSize) {
        this.schedules = schedules;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${simple-mdm.push.schedule-poll-delay-ms:30000}",
        initialDelayString = "${simple-mdm.push.schedule-initial-delay-ms:30000}")
    public void enqueueDue() {
        schedules.enqueueDue(LocalDateTime.now(ZoneOffset.UTC), batchSize);
    }
}
