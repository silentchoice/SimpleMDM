package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RecordService {
    private static final String EDIT_PERMISSION = "MDM_RECORD_EDIT";

    private final MdmRecordRepository records;
    private final RecordValueRepository values;
    private final ObjectTypeRepository objectTypes;
    private final FieldDefinitionRepository fields;
    private final DepartmentRepository departments;
    private final AuthorizationService authorization;
    private final TypedValueConverter converter;
    private final CurrentUserProvider currentUser;

    public RecordService(MdmRecordRepository records, RecordValueRepository values, ObjectTypeRepository objectTypes,
                         FieldDefinitionRepository fields, DepartmentRepository departments,
                         AuthorizationService authorization, TypedValueConverter converter, CurrentUserProvider currentUser) {
        this.records = records; this.values = values; this.objectTypes = objectTypes; this.fields = fields;
        this.departments = departments; this.authorization = authorization; this.converter = converter; this.currentUser = currentUser;
    }

    @Transactional
    public RecordView create(CreateRecordCommand command) { return createAs(authenticatedSystemUser(), command); }

    @Transactional
    public RecordView createAs(Long userId, CreateRecordCommand command) {
        validateCommand(command);
        ObjectType objectType = objectTypes.findById(command.objectTypeId())
            .orElseThrow(() -> new BusinessException(404, "Object type not found"));
        if (!command.systemId().equals(objectType.getSystemId())) throw new BusinessException(400, "Object type must belong to the same system");
        Department department = departments.findById(command.departmentId())
            .orElseThrow(() -> new BusinessException(404, "Department not found"));
        if (!command.systemId().equals(department.getSystem().getId())) throw new BusinessException(400, "Department must belong to the same system");
        authorize(userId, command.departmentId());

        List<FieldDefinition> definitions = fields.findByObjectTypeId(command.objectTypeId());
        Map<FieldDefinition, TypedValue> typedValues = convertAll(definitions, command.data());
        rejectDuplicateUniqueValues(typedValues);

        MdmRecord record = records.saveAndFlush(MdmRecord.create(command.systemId(), objectType, command.objectTypeId(),
            department, command.recordCode(), userId));
        List<RecordValue> rows = typedValues.entrySet().stream()
            .map(entry -> RecordValue.create(record, entry.getKey(), entry.getValue(), userId)).toList();
        values.saveAll(rows);
        return view(record);
    }

    @Transactional
    public RecordView update(Long id, long version, Map<String, Object> data) {
        return updateAs(authenticatedSystemUser(), id, version, data);
    }

    @Transactional
    public RecordView updateAs(Long userId, Long id, long version, Map<String, Object> data) {
        if (id == null || data == null) throw new BusinessException(400, "Record ID and data are required");
        MdmRecord record = records.findById(id).orElseThrow(() -> new BusinessException(404, "Record not found"));
        authorize(userId, record.getDepartmentId());
        if (record.getVersion() != version) throw new BusinessException(409, "Record version is stale");

        List<FieldDefinition> definitions = fields.findByObjectTypeId(record.getObjectTypeId());
        Map<String, FieldDefinition> byKey = index(definitions);
        if (!byKey.keySet().containsAll(data.keySet())) throw new BusinessException(400, "Unknown field key");
        List<RecordValue> changed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            FieldDefinition field = byKey.get(entry.getKey());
            TypedValue value = converter.convert(field, entry.getValue());
            validateReference(field, entry.getValue());
            if (field.isRequired() && value.nonNullValueCount() == 0) throw new BusinessException(400, "Field is required");
            if (fieldValueExistsElsewhere(field, value, record.getId())) throw new BusinessException(409, "Duplicate value for unique field");
            RecordValue row = values.findByRecordIdAndFieldDefinitionId(record.getId(), field.getId())
                .orElseThrow(() -> new BusinessException(409, "Record value row is missing"));
            row.apply(value, userId);
            changed.add(row);
        }
        record.touch(userId);
        records.saveAndFlush(record);
        values.saveAll(changed);
        return view(record);
    }

    private Map<FieldDefinition, TypedValue> convertAll(List<FieldDefinition> definitions, Map<String, Object> data) {
        Map<String, FieldDefinition> byKey = index(definitions);
        if (!byKey.keySet().containsAll(data.keySet())) throw new BusinessException(400, "Unknown field key");
        Map<FieldDefinition, TypedValue> converted = new HashMap<>();
        for (FieldDefinition field : definitions) {
            Object raw = data.get(field.getFieldKey());
            TypedValue typed = converter.convert(field, raw);
            validateReference(field, raw);
            if (field.isRequired() && typed.nonNullValueCount() == 0) throw new BusinessException(400, "Field is required");
            converted.put(field, typed);
        }
        return converted;
    }

    private void rejectDuplicateUniqueValues(Map<FieldDefinition, TypedValue> typedValues) {
        for (Map.Entry<FieldDefinition, TypedValue> entry : typedValues.entrySet()) {
            if (entry.getKey().isRequired() && entry.getValue().nonNullValueCount() == 0) throw new BusinessException(400, "Field is required");
            if (fieldValueExistsElsewhere(entry.getKey(), entry.getValue(), null)) throw new BusinessException(409, "Duplicate value for unique field");
        }
    }

    private boolean fieldValueExistsElsewhere(FieldDefinition field, TypedValue value, Long excludedRecordId) {
        if (!field.isRequired() && value.nonNullValueCount() == 0) return false;
        if (!isUnique(field)) return false;
        return Optional.ofNullable(values.findByFieldDefinitionId(field.getId())).orElse(List.of()).stream()
            .filter(existing -> excludedRecordId == null || !excludedRecordId.equals(recordId(existing)))
            .anyMatch(existing -> existing.typedValue().equals(value));
    }

    private Long recordId(RecordValue ignored) { return null; }
    private boolean isUnique(FieldDefinition field) {
        try { java.lang.reflect.Field uniqueValue = FieldDefinition.class.getDeclaredField("uniqueValue"); uniqueValue.setAccessible(true); return Boolean.TRUE.equals(uniqueValue.get(field)); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("Field definition mapping is incomplete", exception); }
    }

    private void validateReference(FieldDefinition field, Object raw) {
        if (field.getDataType() != FieldDataType.REFERENCE || raw == null || raw instanceof String text && text.isBlank()) return;
        TypedValueConverter.ReferenceValue reference = raw instanceof TypedValueConverter.ReferenceValue value ? value
            : throwBadReference();
        MdmRecord target = records.findById(reference.recordId()).orElseThrow(() -> new BusinessException(404, "Referenced record not found"));
        if (!field.getSystemId().equals(target.getSystemId()) || !field.getReferenceObjectTypeId().equals(target.getObjectTypeId())) {
            throw new BusinessException(400, "Referenced record must match the field system and object type");
        }
    }

    private TypedValueConverter.ReferenceValue throwBadReference() { throw new BusinessException(400, "Value does not match field data type"); }
    private Map<String, FieldDefinition> index(List<FieldDefinition> definitions) {
        Map<String, FieldDefinition> result = new HashMap<>();
        for (FieldDefinition definition : definitions) result.put(definition.getFieldKey(), definition);
        return result;
    }
    private void validateCommand(CreateRecordCommand command) {
        if (command == null || command.systemId() == null || command.objectTypeId() == null || command.departmentId() == null
            || command.recordCode() == null || command.recordCode().isBlank() || command.data() == null) {
            throw new BusinessException(400, "System, object type, department, record code, and data are required");
        }
    }
    private Long authenticatedSystemUser() {
        return currentUser.currentSystemUserId().orElseThrow(() -> new BusinessException(401, "No authenticated system user is available"));
    }
    private void authorize(Long userId, Long departmentId) {
        if (userId == null || !authorization.can(userId, EDIT_PERMISSION, departmentId)) throw new BusinessException(403, "User is not authorized to edit this department");
    }
    private RecordView view(MdmRecord record) { return new RecordView(record.getId(), record.getSystemId(), record.getObjectTypeId(), record.getDepartmentId(), record.getRecordCode(), record.getVersion()); }
}
