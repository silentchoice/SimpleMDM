package com.simplemdm.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.model.integration.PushSubscription;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PushServiceIdempotencyTest {
    @Test
    void boundedSnapshotRemainsValidJsonAndDuplicateForOneSubscriptionDoesNotSkipAnother() throws Exception {
        PushSubscriptionRepository subscriptions=mock(PushSubscriptionRepository.class);
        PushLogRepository logs=mock(PushLogRepository.class);
        MdmRecordRepository records=mock(MdmRecordRepository.class);
        PushService service=new PushService(subscriptions,logs,records,160);
        MdmRecord record=mock(MdmRecord.class);
        when(record.getId()).thenReturn(41L);when(record.getSystemId()).thenReturn(7L);
        when(record.getObjectTypeId()).thenReturn(8L);when(record.getDepartmentId()).thenReturn(9L);
        when(record.getRecordCode()).thenReturn(("quote\"\\\\\\n\\uD83D\\uDE00").repeat(80));when(record.getVersion()).thenReturn(3L);
        when(records.findById(41L)).thenReturn(Optional.of(record));
        when(subscriptions.findActiveForEvent(7L,8L,"RECORD_CHANGED")).thenReturn(List.of(
            PushSubscription.active(901L,7L,801L,8L,"RECORD_CHANGED"),
            PushSubscription.active(902L,7L,802L,8L,"RECORD_CHANGED")));
        when(logs.insertPendingIfAbsent(eq(7L),eq(901L),eq(41L),eq("record:41:version:3"),anyString())).thenReturn(0);

        service.publishRecordChanged(41L);

        verify(logs).insertPendingIfAbsent(eq(7L),eq(901L),eq(41L),eq("record:41:version:3"),argThat(this::validJson));
        verify(logs).insertPendingIfAbsent(eq(7L),eq(902L),eq(41L),eq("record:41:version:3"),argThat(value -> validJson(value) && value.length()<=160));
    }
    private boolean validJson(String value){try{return new ObjectMapper().readTree(value).get("record_id").asLong()==41L;}catch(Exception e){return false;}}
}
