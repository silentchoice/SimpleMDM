package com.simplemdm.service.workflow;

import com.simplemdm.dto.mdm.MasterChildChangeRequest;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalAction;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalChildChange;
import com.simplemdm.model.workflow.ApprovalChildValueChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.ApprovalActionRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.service.mdm.CreateFieldCommand;
import com.simplemdm.service.mdm.CurrentUserProvider;
import com.simplemdm.service.mdm.TypedValueConverter;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApprovalDraftService {
    private static final String EDIT_PERMISSION = "MDM_RECORD_EDIT";

    private final ApprovalRequestRepository requests;
    private final ApprovalChangeRepository changes;
    private final ApprovalChildChangeRepository childChanges;
    private final ApprovalChildValueChangeRepository childValueChanges;
    private final ApprovalActionRepository actions;
    private final ObjectTypeRepository objectTypes;
    private final ChildTypeRepository childTypes;
    private final FieldDefinitionRepository fields;
    private final ChildFieldDefinitionRepository childFields;
    private final MdmRecordRepository records;
    private final RecordValueRepository values;
    private final ChildRecordRepository childRecords;
    private final ChildRecordValueRepository childValues;
    private final DepartmentRepository departments;
    private final AuthorizationService authorization;
    private final TypedValueConverter converter;
    private final CurrentUserProvider currentUser;
    private final UserRepository users;

    public ApprovalDraftService(ApprovalRequestRepository requests, ApprovalChangeRepository changes,
                                ApprovalChildChangeRepository childChanges,
                                ApprovalChildValueChangeRepository childValueChanges,
                                ApprovalActionRepository actions, ObjectTypeRepository objectTypes,
                                ChildTypeRepository childTypes, FieldDefinitionRepository fields,
                                ChildFieldDefinitionRepository childFields, MdmRecordRepository records,
                                RecordValueRepository values, ChildRecordRepository childRecords,
                                ChildRecordValueRepository childValues, DepartmentRepository departments,
                                AuthorizationService authorization, TypedValueConverter converter,
                                CurrentUserProvider currentUser, UserRepository users) {
        this.requests = requests;
        this.changes = changes;
        this.childChanges = childChanges;
        this.childValueChanges = childValueChanges;
        this.actions = actions;
        this.objectTypes = objectTypes;
        this.childTypes = childTypes;
        this.fields = fields;
        this.childFields = childFields;
        this.records = records;
        this.values = values;
        this.childRecords = childRecords;
        this.childValues = childValues;
        this.departments = departments;
        this.authorization = authorization;
        this.converter = converter;
        this.currentUser = currentUser;
        this.users = users;
    }

    @Transactional
    public Long submit(MasterChildChangeRequest request, Long authenticatedUserId) {
        if (request == null) throw badRequest("Approval change request is required");
        Long actorId = authenticatedAs(authenticatedUserId);
        User actor = activeUser(actorId);
        validateTopLevel(request);
        ObjectType objectType = objectTypes.findBySystemIdAndCode(actor.getSystemId(), request.objectCode())
            .filter(ObjectType::isActive)
            .orElseThrow(() -> new BusinessException(404, "Object type not found"));
        departments.findActiveBySystemIdAndId(actor.getSystemId(), request.departmentId())
            .orElseThrow(() -> new BusinessException(404, "Department not found"));
        if (!authorization.can(actorId, EDIT_PERMISSION, request.departmentId())) {
            throw new BusinessException(403, "Applicant cannot edit this department");
        }

        MasterDraft master = masterDraft(actor.getSystemId(), objectType, request);
        List<ChildDraft> children = childDrafts(actor.getSystemId(), objectType, request, master.record());
        if (request.operation() == MasterChildChangeRequest.Operation.UPDATE
            && master.values().isEmpty() && children.isEmpty()) {
            throw badRequest("Approval requires at least one changed field or child operation");
        }

        ApprovalRequest persisted = requests.save(ApprovalRequest.pending(actor.getSystemId(), objectType.getId(),
            toEntityOperation(request.operation()), request.recordId(),
            request.operation() == MasterChildChangeRequest.Operation.CREATE ? request.recordCode() : null,
            request.departmentId(), actorId, request.expectedVersion()));
        if (!master.values().isEmpty()) {
            changes.saveAll(master.values().stream()
                .map(value -> ApprovalChange.create(actor.getSystemId(), persisted.getId(), value.fieldId(),
                    value.oldValue(), value.newValue()))
                .toList());
        }
        for (ChildDraft child : children) {
            ApprovalChildChange saved = childChanges.save(ApprovalChildChange.create(actor.getSystemId(),
                persisted.getId(), child.changeKey(), child.childTypeId(), child.childRecordId(),
                child.operation(), child.expectedVersion(), child.sortOrder()));
            if (!child.values().isEmpty()) {
                childValueChanges.saveAll(child.values().stream()
                    .map(value -> ApprovalChildValueChange.create(actor.getSystemId(), saved.getId(), value.fieldId(),
                        value.oldValue(), value.newValue()))
                    .toList());
            }
        }
        actions.save(ApprovalAction.submitted(actor.getSystemId(), persisted.getId(), actorId));
        return persisted.getId();
    }

    private MasterDraft masterDraft(Long systemId, ObjectType objectType, MasterChildChangeRequest request) {
        List<FieldDefinition> definitions = fields.findByObjectTypeId(objectType.getId()).stream()
            .filter(field -> "active".equals(field.getStatus())).toList();
        Map<String, FieldDefinition> byKey = indexMasterFields(definitions);
        rejectUnknown(byKey, request.data(), "Unknown field key");
        if (request.operation() == MasterChildChangeRequest.Operation.CREATE) {
            if (records.existsBySystemIdAndObjectTypeIdAndRecordCodeAndDeletedAtIsNull(
                systemId, objectType.getId(), request.recordCode())) {
                throw new BusinessException(409, "Record code already exists");
            }
            List<ValueDraft> pending = new ArrayList<>();
            for (FieldDefinition field : definitions) {
                Object raw = request.data().get(field.getFieldKey());
                TypedValue typed = converter.convert(field, raw);
                validateReference(field, raw);
                rejectDuplicateMaster(field, typed, null);
                pending.add(new ValueDraft(field.getId(), TypedValue.empty(), typed));
            }
            return new MasterDraft(null, pending);
        }

        MdmRecord record = records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(
                systemId, objectType.getId(), request.recordId())
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (!request.departmentId().equals(record.getDepartmentId())) {
            throw new BusinessException(404, "Record not found");
        }
        if (request.recordCode() != null && !request.recordCode().isBlank()
            && !request.recordCode().equals(record.getRecordCode())) {
            throw badRequest("Record code cannot change in an update approval");
        }
        if (record.getVersion() != request.expectedVersion()) throw new BusinessException(409, "Record version conflict");
        Map<Long, RecordValue> oldRows = Optional.ofNullable(values.findByRecordId(record.getId())).orElse(List.of())
            .stream().collect(Collectors.toMap(RecordValue::getFieldDefinitionId, Function.identity()));
        List<ValueDraft> pending = new ArrayList<>();
        for (Map.Entry<String, Object> entry : request.data().entrySet()) {
            FieldDefinition field = byKey.get(entry.getKey());
            RecordValue oldRow = oldRows.get(field.getId());
            TypedValue after = converter.convert(field, entry.getValue());
            validateReference(field, entry.getValue());
            rejectDuplicateMaster(field, after, record.getId());
            TypedValue before = oldRow == null ? TypedValue.empty() : oldRow.typedValue();
            if (!before.sameValueAs(after)) {
                pending.add(new ValueDraft(field.getId(), before, after));
            }
        }
        return new MasterDraft(record, pending);
    }

    private List<ChildDraft> childDrafts(Long systemId, ObjectType objectType,
                                         MasterChildChangeRequest request, MdmRecord parent) {
        List<ChildDraft> result = new ArrayList<>();
        Set<Long> targetedExistingChildren = new HashSet<>();
        int sortOrder = 0;
        for (int groupIndex = 0; groupIndex < request.children().size(); groupIndex++) {
            MasterChildChangeRequest.ChildGroup group = request.children().get(groupIndex);
            if (group == null || group.childCode() == null || group.childCode().isBlank()
                || group.rows() == null || group.rows().isEmpty()) {
                throw badRequest("Child code and rows are required");
            }
            ChildType childType = childTypes.findBySystemIdAndObjectTypeIdAndCode(
                systemId, objectType.getId(), group.childCode())
                .filter(type -> "active".equals(type.getStatus()))
                .orElseThrow(() -> new BusinessException(404, "Child type not found"));
            List<ChildFieldDefinition> definitions = childFields.findByChildTypeId(childType.getId()).stream()
                .filter(field -> "active".equals(field.getStatus())).toList();
            Map<String, ChildFieldDefinition> byKey = definitions.stream().collect(Collectors.toMap(
                ChildFieldDefinition::getFieldKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));
            for (int rowIndex = 0; rowIndex < group.rows().size(); rowIndex++) {
                MasterChildChangeRequest.ChildRow row = group.rows().get(rowIndex);
                validateChildTarget(request.operation(), row);
                if (row.id() != null && !targetedExistingChildren.add(row.id())) {
                    throw badRequest("Duplicate child target in approval request");
                }
                String changeKey = group.childCode() + ":" + groupIndex + ":" + rowIndex;
                ChildDraft draft = switch (row.operation()) {
                    case CREATE -> createChildDraft(systemId, childType, definitions, byKey, row,
                        changeKey, sortOrder);
                    case UPDATE -> updateChildDraft(systemId, parent, childType, byKey, row,
                        changeKey, sortOrder);
                    case DELETE -> deleteChildDraft(systemId, parent, childType, definitions, row,
                        changeKey, sortOrder);
                };
                if (draft != null) result.add(draft);
                sortOrder++;
            }
        }
        return result;
    }

    private ChildDraft createChildDraft(Long systemId, ChildType childType,
                                        List<ChildFieldDefinition> definitions,
                                        Map<String, ChildFieldDefinition> byKey,
                                        MasterChildChangeRequest.ChildRow row,
                                        String changeKey, int sortOrder) {
        rejectUnknown(byKey, row.data(), "Unknown child field key");
        List<ValueDraft> pending = new ArrayList<>();
        for (ChildFieldDefinition field : definitions) {
            Object raw = row.data().get(field.getFieldKey());
            TypedValue typed = convert(field, raw);
            validateChildReference(field, raw);
            rejectDuplicateChild(field, typed, null);
            pending.add(new ValueDraft(field.getId(), TypedValue.empty(), typed));
        }
        return new ChildDraft(changeKey, childType.getId(), null, ApprovalChildChange.Operation.CREATE,
            null, sortOrder, pending);
    }

    private ChildDraft updateChildDraft(Long systemId, MdmRecord parent, ChildType childType,
                                        Map<String, ChildFieldDefinition> byKey,
                                        MasterChildChangeRequest.ChildRow row,
                                        String changeKey, int sortOrder) {
        ChildRecord child = existingChild(systemId, parent, childType, row);
        rejectUnknown(byKey, row.data(), "Unknown child field key");
        Map<Long, ChildRecordValue> oldRows = Optional.ofNullable(
                childValues.findByChildRecordIdIn(List.of(child.getId())))
            .orElse(List.of()).stream().collect(Collectors.toMap(
                ChildRecordValue::getFieldDefinitionId, Function.identity()));
        List<ValueDraft> pending = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.data().entrySet()) {
            ChildFieldDefinition field = byKey.get(entry.getKey());
            ChildRecordValue oldRow = oldRows.get(field.getId());
            TypedValue after = convert(field, entry.getValue());
            validateChildReference(field, entry.getValue());
            rejectDuplicateChild(field, after, child.getId());
            TypedValue before = oldRow == null ? TypedValue.empty() : oldRow.typedValue();
            if (!before.sameValueAs(after)) {
                pending.add(new ValueDraft(field.getId(), before, after));
            }
        }
        if (pending.isEmpty()) return null;
        return new ChildDraft(changeKey, childType.getId(), child.getId(), ApprovalChildChange.Operation.UPDATE,
            row.expectedVersion(), sortOrder, pending);
    }

    private ChildDraft deleteChildDraft(Long systemId, MdmRecord parent, ChildType childType,
                                        List<ChildFieldDefinition> definitions,
                                        MasterChildChangeRequest.ChildRow row,
                                        String changeKey, int sortOrder) {
        if (row.data() != null && !row.data().isEmpty()) throw badRequest("Child delete data must be empty");
        ChildRecord child = existingChild(systemId, parent, childType, row);
        Map<Long, ChildFieldDefinition> definitionsById = definitions.stream()
            .collect(Collectors.toMap(ChildFieldDefinition::getId, Function.identity()));
        List<ValueDraft> removedValues = Optional.ofNullable(
                childValues.findByChildRecordIdIn(List.of(child.getId())))
            .orElse(List.of()).stream().map(value -> {
                if (!definitionsById.containsKey(value.getFieldDefinitionId())) {
                    throw new BusinessException(409, "Child record field no longer exists");
                }
                return new ValueDraft(value.getFieldDefinitionId(), value.typedValue(), TypedValue.empty());
            }).toList();
        return new ChildDraft(changeKey, childType.getId(), child.getId(), ApprovalChildChange.Operation.DELETE,
            row.expectedVersion(), sortOrder, removedValues);
    }

    private ChildRecord existingChild(Long systemId, MdmRecord parent, ChildType childType,
                                      MasterChildChangeRequest.ChildRow row) {
        if (parent == null) throw badRequest("A create approval may only create child rows");
        ChildRecord child = childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(
                systemId, parent.getId(), childType.getId(), row.id())
            .orElseThrow(() -> new BusinessException(404, "Child record not found"));
        if (child.getVersion() != row.expectedVersion()) throw new BusinessException(409, "Child record version conflict");
        return child;
    }

    private void validateTopLevel(MasterChildChangeRequest request) {
        if (request.operation() == null || request.objectCode() == null || request.objectCode().isBlank()
            || request.departmentId() == null || request.data() == null || request.children() == null) {
            throw badRequest("Operation, object code, department, data, and children are required");
        }
        if (request.operation() == MasterChildChangeRequest.Operation.CREATE) {
            if (request.recordId() != null || request.expectedVersion() != null
                || request.recordCode() == null || request.recordCode().isBlank()) {
                throw badRequest("Create approval requires record code and no existing target");
            }
        } else if (request.recordId() == null || request.expectedVersion() == null) {
            throw badRequest("Update approval requires record ID and version");
        }
    }

    private void validateChildTarget(MasterChildChangeRequest.Operation masterOperation,
                                     MasterChildChangeRequest.ChildRow row) {
        if (row == null || row.operation() == null) throw badRequest("Child operation is required");
        if (row.operation() == MasterChildChangeRequest.ChildOperation.CREATE) {
            if (row.id() != null || row.expectedVersion() != null || row.data() == null) {
                throw badRequest("Child create requires data and no existing target");
            }
            return;
        }
        if (masterOperation == MasterChildChangeRequest.Operation.CREATE) {
            throw badRequest("A create approval may only create child rows");
        }
        if (row.id() == null || row.expectedVersion() == null
            || row.operation() == MasterChildChangeRequest.ChildOperation.UPDATE && row.data() == null) {
            throw badRequest("Child update/delete requires ID and version");
        }
    }

    private Map<String, FieldDefinition> indexMasterFields(List<FieldDefinition> definitions) {
        return definitions.stream().collect(Collectors.toMap(FieldDefinition::getFieldKey, Function.identity(),
            (left, right) -> left, LinkedHashMap::new));
    }

    private <T> void rejectUnknown(Map<String, T> definitions, Map<String, Object> data, String message) {
        if (data == null || !definitions.keySet().containsAll(data.keySet())) throw badRequest(message);
    }

    private TypedValue convert(ChildFieldDefinition field, Object raw) {
        FieldDefinition adapter = FieldDefinition.create(field.getChildTypeId(),
            ObjectType.create(field.getSystemId(), "CHILD_VALUE", "Child value"),
            new CreateFieldCommand(field.getFieldKey(), field.getFieldKey(), field.getDataType(), field.isRequired(),
                field.isUniqueValue(), false, false, field.getMaxLength(), field.getPrecision(), field.getScale(),
                field.getReferenceObjectTypeId(), null, null, 0), null);
        return converter.convert(adapter, raw);
    }

    private void validateReference(FieldDefinition field, Object raw) {
        if (field.getDataType() != FieldDataType.REFERENCE || raw == null
            || raw instanceof String text && text.isBlank()) return;
        TypedValueConverter.ReferenceValue reference = raw instanceof TypedValueConverter.ReferenceValue value
            ? value : throwBadReference();
        records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(
                field.getSystemId(), field.getReferenceObjectTypeId(), reference.recordId())
            .orElseThrow(() -> new BusinessException(404, "Referenced record not found"));
    }

    private void validateChildReference(ChildFieldDefinition field, Object raw) {
        if (field.getDataType() != FieldDataType.REFERENCE || raw == null
            || raw instanceof String text && text.isBlank()) return;
        TypedValueConverter.ReferenceValue reference = raw instanceof TypedValueConverter.ReferenceValue value
            ? value : throwBadReference();
        records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(
                field.getSystemId(), field.getReferenceObjectTypeId(), reference.recordId())
            .orElseThrow(() -> new BusinessException(404, "Referenced record not found"));
    }

    private void rejectDuplicateMaster(FieldDefinition field, TypedValue value, Long excludedRecordId) {
        if (!field.isUniqueValue() || value.nonNullValueCount() == 0) return;
        boolean duplicate = Optional.ofNullable(values.findActiveByFieldDefinitionId(field.getId())).orElse(List.of())
            .stream().anyMatch(existing -> !Objects.equals(excludedRecordId, existing.getRecordId())
                && existing.typedValue().sameValueAs(value));
        if (duplicate) throw new BusinessException(409, "Duplicate value for unique field");
    }

    private void rejectDuplicateChild(ChildFieldDefinition field, TypedValue value, Long excludedChildId) {
        if (!field.isUniqueValue() || value.nonNullValueCount() == 0) return;
        boolean duplicate = Optional.ofNullable(childValues.findActiveByFieldDefinitionId(field.getId()))
            .orElse(List.of())
            .stream().anyMatch(existing -> !Objects.equals(excludedChildId, existing.getChildRecordId())
                && existing.typedValue().sameValueAs(value));
        if (duplicate) throw new BusinessException(409, "Duplicate value for unique child field");
    }

    private TypedValueConverter.ReferenceValue throwBadReference() {
        throw badRequest("Value does not match field data type");
    }

    private Long authenticatedAs(Long claimedActorId) {
        Long actual = currentUser.currentSystemUserId()
            .orElseThrow(() -> new BusinessException(401, "No authenticated system user"));
        if (claimedActorId == null || !actual.equals(claimedActorId)) {
            throw new BusinessException(403, "Claimed user is not the authenticated user");
        }
        return actual;
    }

    private User activeUser(Long id) {
        User user = users.findById(id)
            .orElseThrow(() -> new BusinessException(401, "Authenticated user not found"));
        if (!user.isActive() || !user.isSystemActive()) {
            throw new BusinessException(403, "Authenticated user is inactive");
        }
        return user;
    }

    private ApprovalRequest.Operation toEntityOperation(MasterChildChangeRequest.Operation operation) {
        return ApprovalRequest.Operation.valueOf(operation.name());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    private record MasterDraft(MdmRecord record, List<ValueDraft> values) { }
    private record ValueDraft(Long fieldId, TypedValue oldValue, TypedValue newValue) { }
    private record ChildDraft(String changeKey, Long childTypeId, Long childRecordId,
                              ApprovalChildChange.Operation operation, Long expectedVersion,
                              int sortOrder, List<ValueDraft> values) { }
}
