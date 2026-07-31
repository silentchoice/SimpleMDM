package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
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
    private final ChildRecordRepository childRecords;
    private final ChildRecordValueRepository childValues;
    private final ChildTypeRepository childTypes;
    private final ChildFieldDefinitionRepository childFields;

    public RecordService(MdmRecordRepository records, RecordValueRepository values, ObjectTypeRepository objectTypes,
                         FieldDefinitionRepository fields, DepartmentRepository departments,
                         AuthorizationService authorization, TypedValueConverter converter, CurrentUserProvider currentUser, ChildRecordRepository childRecords, ChildRecordValueRepository childValues, ChildTypeRepository childTypes, ChildFieldDefinitionRepository childFields) {
        this.records = records; this.values = values; this.objectTypes = objectTypes; this.fields = fields;
        this.departments = departments; this.authorization = authorization; this.converter = converter; this.currentUser = currentUser; this.childRecords = childRecords; this.childValues = childValues; this.childTypes = childTypes; this.childFields = childFields;
    }

    @Transactional
    public RecordView create(CreateRecordCommand command) { return createAs(authenticatedSystemUser(), command); }

    @Transactional
    RecordView createAs(Long userId, CreateRecordCommand command) {
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
    RecordView updateAs(Long userId, Long id, long version, Map<String, Object> data) {
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

    @Transactional
    public ChildRecordView createChild(CreateChildRecordCommand command) {
        return createChildAs(authenticatedSystemUser(), command);
    }

    @Transactional
    ChildRecordView createChildAs(Long userId, CreateChildRecordCommand command) {
        if (command == null || command.parentRecordId() == null || command.childTypeId() == null || command.data() == null) {
            throw new BusinessException(400, "Parent record, child type, and data are required");
        }
        MdmRecord parent = records.findById(command.parentRecordId())
            .orElseThrow(() -> new BusinessException(404, "Parent record not found"));
        ChildType childType = childTypes.findById(command.childTypeId())
            .orElseThrow(() -> new BusinessException(404, "Child type not found"));
        if (!parent.getSystemId().equals(childType.getSystemId()) || !parent.getObjectTypeId().equals(childType.getObjectTypeId())) {
            throw new BusinessException(400, "Child type must belong to the parent record context");
        }
        authorize(userId, parent.getDepartmentId());
        List<ChildFieldDefinition> definitions = childFields.findByChildTypeId(command.childTypeId());
        Map<ChildFieldDefinition, TypedValue> typed = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (ChildFieldDefinition field : definitions) {
            keys.add(field.getFieldKey());
            FieldDefinition validationField = FieldDefinition.create(field.getChildTypeId(),
                ObjectType.create(field.getSystemId(), "CHILD_VALUE", "Child value"),
                new CreateFieldCommand(field.getFieldKey(), field.getFieldKey(), field.getDataType(), field.isRequired(),
                    field.isUniqueValue(), false, false, field.getMaxLength(), field.getPrecision(), field.getScale(),
                    field.getReferenceObjectTypeId(), null, null, 0), null);
            Object raw = command.data().get(field.getFieldKey());
            TypedValue value = converter.convert(validationField, raw);
            validateChildReference(field, raw);
            typed.put(field, value);
        }
        if (!keys.containsAll(command.data().keySet())) throw new BusinessException(400, "Unknown child field key");
        for (Map.Entry<ChildFieldDefinition, TypedValue> entry : typed.entrySet()) {
            if (entry.getKey().isUniqueValue() && entry.getValue().nonNullValueCount() > 0
                && Optional.ofNullable(childValues.findByFieldDefinitionId(entry.getKey().getId())).orElse(List.of()).stream()
                    .anyMatch(existing -> existing.typedValue().equals(entry.getValue()))) {
                throw new BusinessException(409, "Duplicate value for unique child field");
            }
        }
        ChildRecord child = childRecords.saveAndFlush(ChildRecord.create(parent, childType, 0, userId));
        childValues.saveAll(typed.entrySet().stream()
            .map(entry -> ChildRecordValue.create(child, entry.getKey(), entry.getValue(), userId)).toList());
        return new ChildRecordView(child.getId(), parent.getId(), child.getChildTypeId(), child.getSystemId(), parent.getDepartmentId(), 0L);
    }
    @Transactional
    public ChildRecordView updateChild(Long id, long version, Map<String, Object> data) {
        return updateChildAs(authenticatedSystemUser(), id, version, data);
    }

    @Transactional
    ChildRecordView updateChildAs(Long userId, Long id, long version, Map<String, Object> data) {
        if (id == null || data == null) throw new BusinessException(400, "Child record ID and data are required");
        ChildRecord child = childRecords.findById(id)
            .orElseThrow(() -> new BusinessException(404, "Child record not found"));
        MdmRecord parent = records.findById(child.getRecordId())
            .orElseThrow(() -> new BusinessException(409, "Parent record is missing"));
        ChildType childType = childTypes.findById(child.getChildTypeId())
            .orElseThrow(() -> new BusinessException(409, "Child type is missing"));
        if (!parent.getSystemId().equals(child.getSystemId()) || !parent.getSystemId().equals(childType.getSystemId())
            || !parent.getObjectTypeId().equals(childType.getObjectTypeId())) {
            throw new BusinessException(409, "Child record context is invalid");
        }
        authorize(userId, parent.getDepartmentId());
        if (child.getVersion() != version) throw new BusinessException(409, "Child record version is stale");

        List<ChildFieldDefinition> definitions = childFields.findByChildTypeId(childType.getId());
        Set<String> keys = new HashSet<>();
        List<ChildRecordValue> changed = new ArrayList<>();
        for (ChildFieldDefinition field : definitions) {
            keys.add(field.getFieldKey());
            FieldDefinition validationField = FieldDefinition.create(field.getChildTypeId(),
                ObjectType.create(field.getSystemId(), "CHILD_VALUE", "Child value"),
                new CreateFieldCommand(field.getFieldKey(), field.getFieldKey(), field.getDataType(), field.isRequired(),
                    field.isUniqueValue(), false, false, field.getMaxLength(), field.getPrecision(), field.getScale(),
                    field.getReferenceObjectTypeId(), null, null, 0), null);
            Object raw = data.get(field.getFieldKey());
            TypedValue value = converter.convert(validationField, raw);
            validateChildReference(field, raw);
            if (field.isRequired() && value.nonNullValueCount() == 0) throw new BusinessException(400, "Field is required");
            if (field.isUniqueValue() && value.nonNullValueCount() > 0
                && Optional.ofNullable(childValues.findByFieldDefinitionId(field.getId())).orElse(List.of()).stream()
                    .anyMatch(existing -> !child.getId().equals(existing.getChildRecordId())
                        && existing.typedValue().equals(value))) {
                throw new BusinessException(409, "Duplicate value for unique child field");
            }
            ChildRecordValue row = childValues.findByChildRecordIdAndFieldDefinitionId(child.getId(), field.getId())
                .orElseThrow(() -> new BusinessException(409, "Child record value row is missing"));
            row.apply(value, userId);
            changed.add(row);
        }
        if (!keys.containsAll(data.keySet())) throw new BusinessException(400, "Unknown child field key");
        child.touch(userId);
        childRecords.saveAndFlush(child);
        childValues.saveAll(changed);
        return new ChildRecordView(child.getId(), parent.getId(), child.getChildTypeId(), child.getSystemId(),
            parent.getDepartmentId(), child.getVersion());
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

    private Long recordId(RecordValue value) { return value.getRecordId(); }
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

    private void validateChildReference(ChildFieldDefinition field, Object raw) {
        if (field.getDataType() != FieldDataType.REFERENCE || raw == null || raw instanceof String text && text.isBlank()) return;
        TypedValueConverter.ReferenceValue reference = raw instanceof TypedValueConverter.ReferenceValue value ? value
            : throwBadReference();
        MdmRecord target = records.findById(reference.recordId())
            .orElseThrow(() -> new BusinessException(404, "Referenced record not found"));
        if (!field.getSystemId().equals(target.getSystemId())
            || !field.getReferenceObjectTypeId().equals(target.getObjectTypeId())) {
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
