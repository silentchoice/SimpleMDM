package com.simplemdm.service.integration;

import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.integration.PushSubscription;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushServiceTest {
    @Test
    void publishMatchesSystemAndObjectTypeAndStoresStableBoundedSnapshot() {
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        PushLogRepository logs = mock(PushLogRepository.class);
        MdmRecordRepository records = mock(MdmRecordRepository.class);
        PushService service = new PushService(subscriptions, logs, records, 160);
        MdmRecord record = mock(MdmRecord.class);
        when(record.getId()).thenReturn(41L);
        when(record.getSystemId()).thenReturn(7L);
        when(record.getObjectTypeId()).thenReturn(8L);
        when(record.getDepartmentId()).thenReturn(9L);
        when(record.getRecordCode()).thenReturn("X".repeat(400));
        when(record.getVersion()).thenReturn(3L);
        when(records.findById(41L)).thenReturn(Optional.of(record));
        PushSubscription matching = PushSubscription.active(901L, 7L, 801L, 8L, "RECORD_CHANGED");
        when(subscriptions.findActiveForEvent(7L, 8L, "RECORD_CHANGED")).thenReturn(List.of(matching));
        when(logs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.publishRecordChanged(41L);

        verify(subscriptions).findActiveForEvent(7L, 8L, "RECORD_CHANGED");
        verify(logs).insertPendingIfAbsent(eq(7L), eq(901L), eq(41L), eq("record:41:version:3"), argThat(snapshot -> snapshot.length() <= 160 && snapshot.contains("\"record_id\":41")));
    }
}
