package com.simplemdm.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.integration.PushSubscription;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushLogOutboxWriter;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushEventServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private PushSubscriptionRepository subscriptions;
    private PushLogRepository logs;
    private PushLogOutboxWriter outbox;
    private MdmRecordRepository records;
    private RecordValueRepository values;
    private FieldDefinitionRepository fields;
    private ChildRecordRepository childRecords;
    private ChildRecordValueRepository childValues;
    private ChildFieldDefinitionRepository childFields;
    private ChildTypeRepository childTypes;
    private UserRepository users;
    private PushEventService service;
    private MdmRecord record;
    private User actor;

    @BeforeEach
    void setUp() {
        subscriptions = mock(PushSubscriptionRepository.class);
        logs = mock(PushLogRepository.class);
        outbox = mock(PushLogOutboxWriter.class);
        records = mock(MdmRecordRepository.class);
        values = mock(RecordValueRepository.class);
        fields = mock(FieldDefinitionRepository.class);
        childRecords = mock(ChildRecordRepository.class);
        childValues = mock(ChildRecordValueRepository.class);
        childFields = mock(ChildFieldDefinitionRepository.class);
        childTypes = mock(ChildTypeRepository.class);
        users = mock(UserRepository.class);
        service = new PushEventService(subscriptions, logs, outbox, records, values, fields, childRecords,
            childValues, childFields, childTypes, users, JSON, 1_000_000);

        actor = mock(User.class);
        when(actor.getId()).thenReturn(7L);
        when(actor.getSystemId()).thenReturn(10L);
        when(actor.isActive()).thenReturn(true);
        when(actor.isSystemActive()).thenReturn(true);
        when(users.findWithContextById(7L)).thenReturn(Optional.of(actor));

        record = mock(MdmRecord.class);
        when(record.getId()).thenReturn(41L);
        when(record.getSystemId()).thenReturn(10L);
        when(record.getObjectTypeId()).thenReturn(20L);
        when(record.getDepartmentId()).thenReturn(30L);
        when(record.getRecordCode()).thenReturn("EMP-41");
        when(record.getStatus()).thenReturn("active");
        when(record.isActive()).thenReturn(true);
        when(record.getVersion()).thenReturn(4L);
        when(records.findBySystemIdAndId(10L, 41L)).thenReturn(Optional.of(record));
        when(subscriptions.findActiveForEvent(10L, 20L, "RECORD_CHANGED")).thenReturn(List.of(
            PushSubscription.active(91L, 10L, 81L, 20L, "RECORD_CHANGED")));
    }

    @Test
    void automaticEventUsesStableIdAndCompleteEffectiveMasterChildSnapshot() throws Exception {
        FieldDefinition publicMaster = masterField(101L, "name");
        FieldDefinition privateMaster = masterField(102L, "private_pay_grade");
        when(fields.findByObjectTypeId(20L)).thenReturn(List.of(publicMaster, privateMaster));
        List<RecordValue> masterValues = List.of(
            masterValue(41L, 101L, "Alice"), masterValue(41L, 102L, "P9"));
        when(values.findByRecordId(41L)).thenReturn(masterValues);

        ChildType childType = mock(ChildType.class);
        when(childType.getId()).thenReturn(201L);
        when(childType.getSystemId()).thenReturn(10L);
        when(childType.getCode()).thenReturn("part_time");
        when(childType.getStatus()).thenReturn("active");
        ChildRecord child = mock(ChildRecord.class);
        when(child.getId()).thenReturn(301L);
        when(child.getChildTypeId()).thenReturn(201L);
        when(child.getStatus()).thenReturn("active");
        when(child.getVersion()).thenReturn(2L);
        when(childRecords.findBySystemIdAndRecordIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(10L, 41L))
            .thenReturn(List.of(child));
        when(childTypes.findAllById(List.of(201L))).thenReturn(List.of(childType));
        ChildFieldDefinition sharedChild = childField(401L, "company", true);
        ChildFieldDefinition privateChild = childField(402L, "monthly_income", false);
        when(childFields.findByChildTypeId(201L)).thenReturn(List.of(sharedChild, privateChild));
        List<ChildRecordValue> effectiveChildValues = List.of(
            childValue(301L, 401L, "Acme"), childValue(301L, 402L, "9000"));
        when(childValues.findByChildRecordIdIn(List.of(301L))).thenReturn(effectiveChildValues);

        service.enqueueApprovedRecord(41L, 7L);
        service.enqueueApprovedRecord(41L, 7L);

        ArgumentCaptor<String> snapshots = ArgumentCaptor.forClass(String.class);
        verify(outbox, times(2)).insertAutomatic(eq(10L), eq(91L), eq(41L),
            eq("record:41:version:4"), snapshots.capture(), eq("10:81:20:41:4"));
        JsonNode snapshot = JSON.readTree(snapshots.getAllValues().get(0));
        assertThat(snapshot.path("record_id").asLong()).isEqualTo(41L);
        assertThat(snapshot.path("record_code").asText()).isEqualTo("EMP-41");
        assertThat(snapshot.path("data").path("name").asText()).isEqualTo("Alice");
        assertThat(snapshot.path("data").path("private_pay_grade").asText()).isEqualTo("P9");
        assertThat(snapshot.path("children").get(0).path("child_type").asText()).isEqualTo("part_time");
        assertThat(snapshot.path("children").get(0).path("data").path("company").asText())
            .isEqualTo("Acme");
        assertThat(snapshot.path("children").get(0).path("data").path("monthly_income").asText())
            .isEqualTo("9000");
        assertThat(snapshots.getAllValues()).containsOnly(snapshots.getAllValues().get(0));
        assertThat(snapshots.getAllValues().get(0)).doesNotContain("endpoint_url", "authorization", "credential");
    }

    @Test
    void repeatedManualDistributionOfTheSameVersionReturnsTheExistingQueueTask() {
        when(fields.findByObjectTypeId(20L)).thenReturn(List.of());
        when(values.findByRecordId(41L)).thenReturn(List.of());
        when(childRecords.findBySystemIdAndRecordIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(10L, 41L))
            .thenReturn(List.of());
        PushLog queued = PushLog.manual(10L, 91L, 41L, "manual:first", "{}", 7L,
            "operator requested");
        ReflectionTestUtils.setField(queued, "id", 501L);
        when(outbox.insertManual(eq(10L), eq(91L), eq(41L), anyString(), anyString(), eq(7L),
            eq("operator requested"), eq("10:81:20:41:4"))).thenReturn(1, 0);
        when(logs.findBySystemIdAndActiveDedupKey(10L, "10:81:20:41:4"))
            .thenReturn(Optional.of(queued));

        List<Long> first = service.enqueueManualSnapshot(41L, 7L, "operator requested");
        List<Long> duplicate = service.enqueueManualSnapshot(41L, 7L, "operator requested");

        assertThat(first).containsExactly(501L);
        assertThat(duplicate).containsExactly(501L);
        verify(outbox, times(2)).insertManual(eq(10L), eq(91L), eq(41L), anyString(), anyString(),
            eq(7L), eq("operator requested"), eq("10:81:20:41:4"));
        verify(logs, times(2)).findBySystemIdAndActiveDedupKey(10L, "10:81:20:41:4");
    }

    @Test
    void manualRetryRequeuesSameLogicalEventWithoutReplacingSnapshotOrTrigger() {
        PushLog failed = PushLog.pending(10L, 91L, 41L, "record:41:version:4", "{\"original\":true}");
        ReflectionTestUtils.setField(failed, "id", 51L);
        ReflectionTestUtils.setField(failed, "status", "FAILED");
        ReflectionTestUtils.setField(failed, "retryCount", 3);
        when(logs.findBySystemIdAndId(10L, 51L)).thenReturn(Optional.of(failed));
        when(logs.requeueFailed(eq(51L), eq(10L), eq(7L),
            eq("retry after downstream recovery"), any(java.time.LocalDateTime.class))).thenReturn(1);

        Long retriedId = service.retryFailed(51L, 7L, "retry after downstream recovery");

        assertThat(retriedId).isEqualTo(51L);
        assertThat(failed.getEventId()).isEqualTo("record:41:version:4");
        assertThat(failed.getRequestSnapshot()).isEqualTo("{\"original\":true}");
        assertThat(failed.getTriggerType()).isEqualTo(PushLog.TriggerType.AUTOMATIC);
        verify(logs, org.mockito.Mockito.never()).saveAndFlush(any(PushLog.class));
    }

    @Test
    void pendingCancellationIsAuditedButRunningAndCancelledTasksConflict() {
        PushLog pending = PushLog.pending(10L, 91L, 41L, "event", "{}");
        ReflectionTestUtils.setField(pending, "id", 51L);
        when(logs.findBySystemIdAndId(10L, 51L)).thenReturn(Optional.of(pending));
        when(logs.cancelPending(eq(51L), eq(10L), eq(7L), eq("不再需要"),
            any(java.time.LocalDateTime.class))).thenReturn(1);

        assertThat(service.cancelPending(51L, 7L, " 不再需要 ")).isEqualTo(51L);

        ReflectionTestUtils.setField(pending, "status", "RUNNING");
        assertThatThrownBy(() -> service.cancelPending(51L, 7L, "too late"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(409));
        ReflectionTestUtils.setField(pending, "status", "CANCELLED");
        assertThatThrownBy(() -> service.retryFailed(51L, 7L, null))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void rejectsReasonBeyondDatabaseAuditBound() {
        assertThatThrownBy(() -> service.enqueueManualSnapshot(41L, 7L, "x".repeat(513)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(400));
    }

    @Test
    void requestSnapshotLimitIsEnforcedOnUtf8BytesWithoutTruncatingJson() {
        PushEventService byteLimited = new PushEventService(subscriptions, logs, outbox, records, values, fields,
            childRecords, childValues, childFields, childTypes, users, JSON, 1024);
        FieldDefinition field = masterField(101L, "multibyte_text");
        RecordValue multibyteValue = masterValue(41L, 101L, "中".repeat(400));
        when(fields.findByObjectTypeId(20L)).thenReturn(List.of(field));
        when(values.findByRecordId(41L)).thenReturn(List.of(multibyteValue));
        when(childRecords.findBySystemIdAndRecordIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(10L, 41L))
            .thenReturn(List.of());

        assertThatThrownBy(() -> byteLimited.enqueueApprovedRecord(41L, 7L))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(413));
    }

    private FieldDefinition masterField(Long id, String key) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getId()).thenReturn(id);
        when(field.getFieldKey()).thenReturn(key);
        when(field.getStatus()).thenReturn("active");
        return field;
    }

    private RecordValue masterValue(Long recordId, Long fieldId, String content) {
        RecordValue value = mock(RecordValue.class);
        when(value.getRecordId()).thenReturn(recordId);
        when(value.getFieldDefinitionId()).thenReturn(fieldId);
        when(value.typedValue()).thenReturn(typed(content));
        return value;
    }

    private ChildFieldDefinition childField(Long id, String key, boolean shared) {
        ChildFieldDefinition field = mock(ChildFieldDefinition.class);
        when(field.getId()).thenReturn(id);
        when(field.getFieldKey()).thenReturn(key);
        when(field.isShared()).thenReturn(shared);
        when(field.getStatus()).thenReturn("active");
        return field;
    }

    private ChildRecordValue childValue(Long childId, Long fieldId, String content) {
        ChildRecordValue value = mock(ChildRecordValue.class);
        when(value.getChildRecordId()).thenReturn(childId);
        when(value.getFieldDefinitionId()).thenReturn(fieldId);
        when(value.typedValue()).thenReturn(typed(content));
        return value;
    }

    private TypedValue typed(String content) {
        return new TypedValue(content, null, null, null, null, null, null, null);
    }
}
