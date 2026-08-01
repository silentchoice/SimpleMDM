package com.simplemdm.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PushEventService {
    private static final String EVENT_TYPE = "RECORD_CHANGED";
    private static final int REASON_LIMIT = 512;
    private static final int MEDIUMTEXT_MAX_BYTES = 16_777_215;

    private final PushSubscriptionRepository subscriptions;
    private final PushLogRepository logs;
    private final PushLogOutboxWriter outbox;
    private final MdmRecordRepository records;
    private final RecordValueRepository values;
    private final FieldDefinitionRepository fields;
    private final ChildRecordRepository childRecords;
    private final ChildRecordValueRepository childValues;
    private final ChildFieldDefinitionRepository childFields;
    private final ChildTypeRepository childTypes;
    private final UserRepository users;
    private final ObjectMapper json;
    private final int snapshotLimit;

    public PushEventService(PushSubscriptionRepository subscriptions, PushLogRepository logs,
                            PushLogOutboxWriter outbox,
                            MdmRecordRepository records, RecordValueRepository values,
                            FieldDefinitionRepository fields, ChildRecordRepository childRecords,
                            ChildRecordValueRepository childValues,
                            ChildFieldDefinitionRepository childFields,
                            ChildTypeRepository childTypes, UserRepository users, ObjectMapper json,
                            @Value("${simple-mdm.push.request-snapshot-limit:1000000}") int snapshotLimit) {
        this.subscriptions = subscriptions;
        this.logs = logs;
        this.outbox = outbox;
        this.records = records;
        this.values = values;
        this.fields = fields;
        this.childRecords = childRecords;
        this.childValues = childValues;
        this.childFields = childFields;
        this.childTypes = childTypes;
        this.users = users;
        this.json = json;
        this.snapshotLimit = Math.max(1024, Math.min(MEDIUMTEXT_MAX_BYTES, snapshotLimit));
    }

    @Transactional
    public List<Long> enqueueApprovedRecord(Long recordId, Long actorId) {
        User actor = actor(actorId);
        MdmRecord record = record(actor.getSystemId(), recordId);
        String eventId = "record:" + record.getId() + ":version:" + record.getVersion();
        String snapshot = snapshot(record);
        List<PushSubscription> matching = matching(record);
        for (PushSubscription subscription : matching) {
            outbox.insertAutomatic(record.getSystemId(), subscription.getId(), record.getId(),
                eventId, snapshot, dedupKey(record, subscription));
        }
        return matching.stream().map(PushSubscription::getId).toList();
    }

    @Transactional
    public List<Long> enqueueManualSnapshot(Long recordId, Long actorId, String reason) {
        User actor = actor(actorId);
        MdmRecord record = record(actor.getSystemId(), recordId);
        String normalizedReason = reason(reason);
        String eventId = "manual:" + UUID.randomUUID();
        String snapshot = snapshot(record);
        List<Long> ids = new ArrayList<>();
        for (PushSubscription subscription : matching(record)) {
            String dedupKey = dedupKey(record, subscription);
            outbox.insertManual(record.getSystemId(), subscription.getId(), record.getId(), eventId,
                snapshot, actorId, normalizedReason, dedupKey);
            PushLog queued = logs.findBySystemIdAndActiveDedupKey(record.getSystemId(), dedupKey)
                .orElseThrow(() -> new IllegalStateException("Manual push event was not persisted"));
            ids.add(queued.getId());
        }
        return ids;
    }

    @Transactional
    public int enqueueScheduledEndpoint(Long systemId, Long endpointId) {
        if (systemId == null || endpointId == null) return 0;
        int queued = 0;
        List<PushSubscription> scheduled = subscriptions
            .findBySystemIdAndEndpointIdAndStatusOrderById(systemId, endpointId, "active");
        for (PushSubscription subscription : scheduled) {
            if (!systemId.equals(subscription.getSystemId()) || subscription.getObjectTypeId() == null) continue;
            List<MdmRecord> current = records
                .findBySystemIdAndObjectTypeIdAndStatusAndDeletedAtIsNullOrderById(
                    systemId, subscription.getObjectTypeId(), "active");
            for (MdmRecord record : current) {
                if (!record.isActive()) continue;
                String eventId = "scheduled:record:" + record.getId() + ":version:" + record.getVersion();
                queued += outbox.insertScheduled(systemId, subscription.getId(), record.getId(), eventId,
                    snapshot(record), dedupKey(record, subscription));
            }
        }
        return queued;
    }

    @Transactional
    public Long retryFailed(Long logId, Long actorId, String reason) {
        User actor = actor(actorId);
        PushLog source = logs.findBySystemIdAndId(actor.getSystemId(), logId)
            .orElseThrow(() -> new BusinessException(404, "Push log not found"));
        String normalizedReason = reason(reason);
        if (!"FAILED".equals(source.getStatus()) || logs.requeueFailed(source.getId(), source.getSystemId(),
            actorId, normalizedReason, java.time.LocalDateTime.now()) != 1) {
            throw new BusinessException(409, "Only failed push logs can be retried");
        }
        return source.getId();
    }

    @Transactional
    public Long cancelPending(Long logId, Long actorId, String reason) {
        User actor = actor(actorId);
        PushLog source = logs.findBySystemIdAndId(actor.getSystemId(), logId)
            .orElseThrow(() -> new BusinessException(404, "Push log not found"));
        String normalizedReason = reason(reason);
        if (!"PENDING".equals(source.getStatus()) || logs.cancelPending(source.getId(), source.getSystemId(),
            actorId, normalizedReason, java.time.LocalDateTime.now()) != 1) {
            throw new BusinessException(409, "Only pending push logs can be cancelled");
        }
        return source.getId();
    }

    private User actor(Long actorId) {
        if (actorId == null) throw new BusinessException(401, "System user required");
        User actor = users.findWithContextById(actorId)
            .filter(User::isActive).filter(User::isSystemActive)
            .orElseThrow(() -> new BusinessException(401, "System user required"));
        if (!actorId.equals(actor.getId())) throw new BusinessException(401, "System user required");
        return actor;
    }

    private MdmRecord record(Long systemId, Long recordId) {
        if (recordId == null) throw new BusinessException(404, "Record not found");
        return records.findBySystemIdAndId(systemId, recordId)
            .filter(MdmRecord::isActive)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
    }

    private List<PushSubscription> matching(MdmRecord record) {
        return subscriptions.findActiveForEvent(record.getSystemId(), record.getObjectTypeId(), EVENT_TYPE)
            .stream().filter(subscription -> record.getSystemId().equals(subscription.getSystemId()))
            .toList();
    }

    private String dedupKey(MdmRecord record, PushSubscription subscription) {
        return record.getSystemId() + ":" + subscription.getEndpointId() + ":"
            + record.getObjectTypeId() + ":" + record.getId() + ":" + record.getVersion();
    }

    private String snapshot(MdmRecord record) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("record_id", record.getId());
        envelope.put("system_id", record.getSystemId());
        envelope.put("object_type_id", record.getObjectTypeId());
        envelope.put("department_id", record.getDepartmentId());
        envelope.put("record_code", record.getRecordCode());
        envelope.put("status", record.getStatus());
        envelope.put("version", record.getVersion());
        envelope.put("data", masterData(record));
        envelope.put("children", childData(record));
        String serialized = write(envelope);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > snapshotLimit) {
            throw new BusinessException(413, "Push snapshot exceeds configured limit");
        }
        return serialized;
    }

    private Map<String, Object> masterData(MdmRecord record) {
        Map<Long, FieldDefinition> definitions = fields.findByObjectTypeId(record.getObjectTypeId()).stream()
            .filter(field -> "active".equals(field.getStatus()))
            .sorted(Comparator.comparingInt(FieldDefinition::getSortOrder).thenComparing(FieldDefinition::getId))
            .collect(Collectors.toMap(FieldDefinition::getId, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
        Map<Long, RecordValue> current = values.findByRecordId(record.getId()).stream()
            .collect(Collectors.toMap(RecordValue::getFieldDefinitionId, Function.identity()));
        Map<String, Object> data = new LinkedHashMap<>();
        definitions.forEach((id, field) -> data.put(field.getFieldKey(),
            Optional.ofNullable(current.get(id)).map(RecordValue::typedValue).map(this::untyped).orElse(null)));
        return data;
    }

    private List<Map<String, Object>> childData(MdmRecord record) {
        List<ChildRecord> children = childRecords
            .findBySystemIdAndRecordIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(record.getSystemId(), record.getId())
            .stream().filter(child -> "active".equals(child.getStatus())).toList();
        if (children.isEmpty()) return List.of();
        List<Long> typeIds = children.stream().map(ChildRecord::getChildTypeId).distinct().sorted().toList();
        Map<Long, ChildType> types = childTypes.findAllById(typeIds).stream()
            .filter(type -> record.getSystemId().equals(type.getSystemId()))
            .filter(type -> "active".equals(type.getStatus()))
            .collect(Collectors.toMap(ChildType::getId, Function.identity()));
        Map<Long, List<ChildFieldDefinition>> definitions = new HashMap<>();
        for (Long typeId : typeIds) {
            definitions.put(typeId, childFields.findByChildTypeId(typeId).stream()
                .filter(field -> "active".equals(field.getStatus()))
                .sorted(Comparator.comparing(ChildFieldDefinition::getId)).toList());
        }
        Map<Long, List<ChildRecordValue>> current = childValues
            .findByChildRecordIdIn(children.stream().map(ChildRecord::getId).toList()).stream()
            .collect(Collectors.groupingBy(ChildRecordValue::getChildRecordId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChildRecord child : children) {
            ChildType type = types.get(child.getChildTypeId());
            if (type == null) continue;
            Map<Long, ChildRecordValue> valuesByField = current.getOrDefault(child.getId(), List.of()).stream()
                .collect(Collectors.toMap(ChildRecordValue::getFieldDefinitionId, Function.identity()));
            Map<String, Object> data = new LinkedHashMap<>();
            for (ChildFieldDefinition definition : definitions.getOrDefault(type.getId(), List.of())) {
                ChildRecordValue value = valuesByField.get(definition.getId());
                data.put(definition.getFieldKey(), value == null ? null : untyped(value.typedValue()));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", child.getId());
            row.put("child_type_id", type.getId());
            row.put("child_type", type.getCode());
            row.put("status", child.getStatus());
            row.put("version", child.getVersion());
            row.put("data", data);
            result.add(row);
        }
        return result;
    }

    private Object untyped(TypedValue value) {
        if (value == null) return null;
        if (value.stringValue() != null) return value.stringValue();
        if (value.textValue() != null) return value.textValue();
        if (value.integerValue() != null) return value.integerValue();
        if (value.decimalValue() != null) return value.decimalValue();
        if (value.booleanValue() != null) return value.booleanValue();
        if (value.dateValue() != null) return value.dateValue();
        if (value.datetimeValue() != null) return value.datetimeValue();
        return value.referenceRecordId();
    }

    private String reason(String value) {
        if (value == null) return null;
        if (value.length() > REASON_LIMIT) throw new BusinessException(400, "Reason exceeds 512 characters");
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize push snapshot", exception);
        }
    }
}
