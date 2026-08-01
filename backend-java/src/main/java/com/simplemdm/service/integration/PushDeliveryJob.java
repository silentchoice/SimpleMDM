package com.simplemdm.service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class PushDeliveryJob {
    private final PushDeliveryService delivery;
    private final int batchSize;

    public PushDeliveryJob(PushDeliveryService delivery,
                           @Value("${simple-mdm.push.batch-size:20}") int batchSize) {
        this.delivery = delivery;
        this.batchSize = Math.max(1, Math.min(100, batchSize));
    }

    @Scheduled(fixedDelayString = "${simple-mdm.push.poll-delay-ms:5000}",
        initialDelayString = "${simple-mdm.push.initial-delay-ms:5000}")
    public void deliverPending() {
        for (Long logId : delivery.deliveryCandidates(batchSize)) {
            delivery.deliver(logId);
        }
    }
}
