package com.simplemdm.service.mdm;

import com.simplemdm.dto.mdm.RecordResponse;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.service.system.RecordAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecordProjectionService {
    private final FieldDefinitionRepository fields;
    private final RecordValueRepository values;
    private final ChildRecordRepository childRecords;
    private final ChildFieldDefinitionRepository childFields;
    private final ChildRecordValueRepository childValues;
    private final RecordAccessService access;

    public RecordProjectionService(FieldDefinitionRepository fields, RecordValueRepository values,
                                   ChildRecordRepository childRecords,
                                   ChildFieldDefinitionRepository childFields,
                                   ChildRecordValueRepository childValues, RecordAccessService access) {
        this.fields = fields;
        this.values = values;
        this.childRecords = childRecords;
        this.childFields = childFields;
        this.childValues = childValues;
        this.access = access;
    }

    public List<RecordResponse> records(User user, String objectCode, Long objectTypeId,
                                        List<MdmRecord> candidates) {
        return records(access.snapshot(user), objectCode, objectTypeId, candidates);
    }

    public List<RecordResponse> records(RecordAccessService.Snapshot snapshot, String objectCode,
                                        Long objectTypeId, List<MdmRecord> candidates) {
        List<MdmRecord> readable = candidates.stream()
            .filter(MdmRecord::isActive)
            .filter(record -> snapshot.decision(record.getDepartmentId()) != RecordAccessService.Decision.DENY)
            .toList();
        if (readable.isEmpty()) return List.of();
        Map<Long, FieldDefinition> definitions = fields.findByObjectTypeId(objectTypeId).stream()
            .filter(field -> "active".equals(field.getStatus()))
            .collect(Collectors.toMap(FieldDefinition::getId, Function.identity()));
        Map<Long, Map<String, Object>> data = new HashMap<>();
        for (MdmRecord record : readable) data.put(record.getId(), new LinkedHashMap<>());
        List<Long> recordIds = readable.stream().map(MdmRecord::getId).toList();
        for (RecordValue value : values.findByRecordIdIn(recordIds)) {
            FieldDefinition field = definitions.get(value.getFieldDefinitionId());
            Map<String, Object> target = data.get(value.getRecordId());
            if (field != null && target != null) target.put(field.getFieldKey(), untyped(value.typedValue()));
        }
        return readable.stream().map(record -> new RecordResponse(record.getId(), objectCode,
            record.getDepartmentId(), record.getRecordCode(), record.getStatus(), record.getVersion(),
            data.get(record.getId()))).toList();
    }

    public List<Map<String, Object>> children(User user, MdmRecord parent, String childCode,
                                              ChildType childType) {
        return children(access.snapshot(user), user, parent, childCode, childType);
    }

    public List<Map<String, Object>> children(RecordAccessService.Snapshot snapshot, User user,
                                              MdmRecord parent, String childCode, ChildType childType) {
        RecordAccessService.Decision decision = snapshot.decision(parent.getDepartmentId());
        if (decision == RecordAccessService.Decision.DENY) throw new BusinessException(404, "Record not found");
        List<ChildFieldDefinition> definitions = decision == RecordAccessService.Decision.SHARED
            ? childFields.findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(
                    childType.getId(), "active").stream()
                .filter(field -> "active".equals(field.getStatus()))
                .toList()
            : null;
        if (decision == RecordAccessService.Decision.SHARED && definitions.isEmpty()) return List.of();
        List<ChildRecord> children = childRecords.findBySystemIdAndRecordIdAndChildTypeId(
                user.getSystemId(), parent.getId(), childType.getId()).stream()
            .filter(child -> "active".equals(child.getStatus()) && child.getDeletedAt() == null)
            .toList();
        if (children.isEmpty()) return List.of();
        if (decision == RecordAccessService.Decision.FULL) {
            definitions = childFields.findByChildTypeId(childType.getId()).stream()
                .filter(field -> "active".equals(field.getStatus())).toList();
        }
        Map<Long, ChildFieldDefinition> byId = definitions.stream()
            .collect(Collectors.toMap(ChildFieldDefinition::getId, Function.identity()));
        Map<Long, Map<String, Object>> data = new HashMap<>();
        for (ChildRecord child : children) data.put(child.getId(), new LinkedHashMap<>());
        List<Long> childIds = children.stream().map(ChildRecord::getId).toList();
        List<Long> fieldIds = definitions.stream().map(ChildFieldDefinition::getId).toList();
        if (!fieldIds.isEmpty()) {
            childValues.findByChildRecordIdInAndFieldDefinitionIdIn(childIds, fieldIds).forEach(value -> {
                ChildFieldDefinition field = byId.get(value.getFieldDefinitionId());
                Map<String, Object> target = data.get(value.getChildRecordId());
                if (field != null && target != null) target.put(field.getFieldKey(), untyped(value.typedValue()));
            });
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChildRecord child : children) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", child.getId());
            row.put("parent_record_id", parent.getId());
            row.put("child_type", childCode);
            row.put("department_id", parent.getDepartmentId());
            row.put("version", child.getVersion());
            row.put("data", data.get(child.getId()));
            result.add(row);
        }
        return result;
    }

    private Object untyped(TypedValue value) {
        if (value.stringValue() != null) return value.stringValue();
        if (value.textValue() != null) return value.textValue();
        if (value.integerValue() != null) return value.integerValue();
        if (value.decimalValue() != null) return value.decimalValue();
        if (value.booleanValue() != null) return value.booleanValue();
        if (value.dateValue() != null) return value.dateValue();
        if (value.datetimeValue() != null) return value.datetimeValue();
        return value.referenceRecordId();
    }
}
