package com.simplemdm.service.workflow;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
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
import com.simplemdm.repository.workflow.ApproverAssignmentRepository;
import com.simplemdm.service.mdm.CurrentUserProvider;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.mdm.CreateFieldCommand;
import com.simplemdm.service.mdm.TypedValueConverter;
import com.simplemdm.service.integration.PushEventService;
import com.simplemdm.service.system.AuthorizationService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApprovalApplyService {
    private static final String REVIEW_PERMISSION = "APPROVAL_REVIEW";

    private final ApprovalRequestRepository requests;
    private final ApprovalChangeRepository changes;
    private final ApprovalChildChangeRepository childChanges;
    private final ApprovalChildValueChangeRepository childValueChanges;
    private final ApprovalActionRepository actions;
    private final ApproverAssignmentRepository assignments;
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
    private final CurrentUserProvider currentUser;
    private final UserRepository users;
    private final TypedValueConverter converter;
    private final PushEventService pushEvents;

    public ApprovalApplyService(ApprovalRequestRepository requests, ApprovalChangeRepository changes,
                                ApprovalChildChangeRepository childChanges,
                                ApprovalChildValueChangeRepository childValueChanges,
                                ApprovalActionRepository actions, ApproverAssignmentRepository assignments,
                                ObjectTypeRepository objectTypes, ChildTypeRepository childTypes,
                                FieldDefinitionRepository fields, ChildFieldDefinitionRepository childFields,
                                MdmRecordRepository records, RecordValueRepository values,
                                ChildRecordRepository childRecords, ChildRecordValueRepository childValues,
                                DepartmentRepository departments, AuthorizationService authorization,
                                CurrentUserProvider currentUser, UserRepository users,
                                TypedValueConverter converter, PushEventService pushEvents) {
        this.requests = requests;
        this.changes = changes;
        this.childChanges = childChanges;
        this.childValueChanges = childValueChanges;
        this.actions = actions;
        this.assignments = assignments;
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
        this.currentUser = currentUser;
        this.users = users;
        this.converter = converter;
        this.pushEvents = pushEvents;
    }

    @Transactional
    public RecordView approve(Long requestId, Long authenticatedApproverId) {
        try {
            return approveLocked(requestId, authenticatedApproverId);
        } catch (PessimisticLockingFailureException exception) {
            throw conflict("Concurrent approval conflict");
        }
    }

    @Transactional
    public void reject(Long requestId, Long authenticatedApproverId, String comment) {
        try {
            ApprovalRequest request = authorizedPendingRequest(requestId, authenticatedApproverId);
            request.reject();
            requests.saveAndFlush(request);
            actions.saveAndFlush(ApprovalAction.rejected(request.getSystemId(), requestId,
                authenticatedApproverId, normalizedComment(comment)));
        } catch (PessimisticLockingFailureException exception) {
            throw conflict("Concurrent approval conflict");
        }
    }

    private RecordView approveLocked(Long requestId, Long authenticatedApproverId) {
        ApprovalRequest request = authorizedPendingRequest(requestId, authenticatedApproverId);
        Long actorId = authenticatedApproverId;

        ObjectType objectType = objectTypes.findBySystemIdAndIdForUpdate(request.getSystemId(), request.getObjectTypeId())
            .filter(ObjectType::isActive)
            .orElseThrow(() -> conflict("Approval object type no longer exists"));
        Department department = departments.findActiveBySystemIdAndId(request.getSystemId(), request.getDepartmentId())
            .orElseThrow(() -> conflict("Approval department no longer exists"));

        List<ApprovalChildChange> requestedChildren = childChanges
            .findByApprovalRequestIdOrderBySortOrderAscIdAsc(requestId);
        lockUniqueValueSpaces(request, requestedChildren);
        MdmRecord record = lockMaster(request);
        Map<Long, ChildRecord> lockedChildren = lockAndValidateChildren(request, record, requestedChildren);

        MdmRecord applied = request.getOperation() == ApprovalRequest.Operation.CREATE
            ? applyCreate(request, objectType, department, actorId)
            : applyUpdate(request, record, actorId);
        applyChildren(request, applied, requestedChildren, lockedChildren, actorId);

        if (request.getOperation() == ApprovalRequest.Operation.CREATE) request.attachCreatedRecord(applied.getId());
        request.approve();
        requests.saveAndFlush(request);
        actions.saveAndFlush(ApprovalAction.approved(request.getSystemId(), requestId, actorId));
        pushEvents.enqueueApprovedRecord(applied.getId(), actorId);
        return view(applied);
    }

    private ApprovalRequest authorizedPendingRequest(Long requestId, Long authenticatedApproverId) {
        if (requestId == null) throw new BusinessException(400, "Approval request ID is required");
        Long actorId = authenticatedAs(authenticatedApproverId);
        User actor = activeUser(actorId);
        ApprovalRequest request = requests.findBySystemIdAndIdForUpdate(actor.getSystemId(), requestId)
            .orElseThrow(() -> new BusinessException(404, "Approval request not found"));
        boolean assigned = assignments.existsActiveAssignment(request.getSystemId(), request.getObjectTypeId(),
            request.getDepartmentId(), actorId);
        if (!assigned || !authorization.canInStrictSelfScope(
            actorId, REVIEW_PERMISSION, request.getDepartmentId())) {
            throw new BusinessException(404, "Approval request not found");
        }
        if (!"PENDING".equals(request.getStatus())) throw conflict("Approval request is not pending");
        return request;
    }

    private String normalizedComment(String comment) {
        if (comment == null) return null;
        if (comment.length() > 2048) throw new BusinessException(400, "Comment exceeds 2048 characters");
        String normalized = comment.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void lockUniqueValueSpaces(ApprovalRequest request,
                                       List<ApprovalChildChange> requestedChildren) {
        fields.findByObjectTypeId(request.getObjectTypeId()).stream()
            .filter(field -> "active".equals(field.getStatus()))
            .filter(FieldDefinition::isUniqueValue)
            .map(FieldDefinition::getId).sorted()
            .forEach(values::findActiveByFieldDefinitionIdForUpdate);
        requestedChildren.stream().map(ApprovalChildChange::getChildTypeId).distinct().sorted()
            .flatMap(childTypeId -> childFields.findByChildTypeId(childTypeId).stream())
            .filter(field -> "active".equals(field.getStatus()))
            .filter(ChildFieldDefinition::isUniqueValue)
            .map(ChildFieldDefinition::getId).distinct().sorted()
            .forEach(childValues::findActiveByFieldDefinitionIdForUpdate);
    }

    private MdmRecord lockMaster(ApprovalRequest request) {
        if (request.getOperation() == ApprovalRequest.Operation.CREATE) {
            if (request.getRecordId() != null || request.getExpectedVersion() != null
                || request.getRecordCode() == null || request.getRecordCode().isBlank()) {
                throw conflict("Create approval target is invalid");
            }
            if (!records.findByRecordCodeForUpdate(request.getSystemId(), request.getObjectTypeId(),
                request.getRecordCode()).isEmpty()) {
                throw conflict("Record code already exists");
            }
            return null;
        }
        if (request.getRecordId() == null || request.getExpectedVersion() == null) {
            throw conflict("Update approval target is invalid");
        }
        MdmRecord record = records.findApprovalTargetForUpdate(request.getSystemId(), request.getObjectTypeId(),
                request.getDepartmentId(), request.getRecordId())
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (record.getVersion() != request.getExpectedVersion()) throw conflict("Record version conflict");
        return record;
    }

    private Map<Long, ChildRecord> lockAndValidateChildren(ApprovalRequest request, MdmRecord record,
                                                            List<ApprovalChildChange> requestedChildren) {
        List<Long> targetIds = requestedChildren.stream().map(ApprovalChildChange::getChildRecordId)
            .filter(Objects::nonNull).distinct().sorted().toList();
        Map<Long, ChildRecord> locked = targetIds.isEmpty() ? Map.of()
            : childRecords.findAllBySystemIdAndIdInForUpdate(request.getSystemId(), targetIds).stream()
                .collect(Collectors.toMap(ChildRecord::getId, Function.identity()));
        if (locked.size() != targetIds.size()) throw new BusinessException(404, "Child record not found");
        Set<String> keys = new HashSet<>();
        Set<Long> existingTargets = new HashSet<>();
        for (ApprovalChildChange change : requestedChildren) {
            if (!request.getSystemId().equals(change.getSystemId()) || !keys.add(change.getChangeKey())) {
                throw conflict("Approval child change context is invalid");
            }
            if (change.getChildRecordId() != null && !existingTargets.add(change.getChildRecordId())) {
                throw conflict("Duplicate child target in approval request");
            }
            ChildType childType = childTypes.findById(change.getChildTypeId())
                .orElseThrow(() -> conflict("Approval child type no longer exists"));
            if (!request.getSystemId().equals(childType.getSystemId())
                || !request.getObjectTypeId().equals(childType.getObjectTypeId())) {
                throw conflict("Approval child type context is invalid");
            }
            if (change.getOperation() == ApprovalChildChange.Operation.CREATE) {
                if (change.getChildRecordId() != null || change.getExpectedVersion() != null) {
                    throw conflict("Child create target is invalid");
                }
                continue;
            }
            if (request.getOperation() == ApprovalRequest.Operation.CREATE || record == null) {
                throw conflict("A create approval may only create child rows");
            }
            ChildRecord child = locked.get(change.getChildRecordId());
            if (child == null || !record.getId().equals(child.getRecordId())
                || !change.getChildTypeId().equals(child.getChildTypeId())) {
                throw new BusinessException(404, "Child record not found");
            }
            if (change.getExpectedVersion() == null || child.getVersion() != change.getExpectedVersion()) {
                throw conflict("Child record version conflict");
            }
        }
        return locked;
    }

    private MdmRecord applyCreate(ApprovalRequest request, ObjectType objectType, Department department, Long actorId) {
        List<FieldDefinition> definitions = fields.findByObjectTypeId(request.getObjectTypeId()).stream()
            .filter(field -> "active".equals(field.getStatus())).toList();
        Map<Long, FieldDefinition> byId = byId(definitions, FieldDefinition::getId);
        List<ApprovalChange> pending = changes.findByApprovalRequestId(request.getId());
        Map<Long, ApprovalChange> pendingByField = byId(pending, ApprovalChange::getFieldDefinitionId);
        if (!byId.keySet().containsAll(pendingByField.keySet())) {
            throw conflict("Approval master field set no longer matches metadata");
        }
        MdmRecord record;
        try {
            record = records.saveAndFlush(MdmRecord.create(request.getSystemId(), objectType,
                request.getObjectTypeId(), department, request.getRecordCode(), actorId));
        } catch (DataIntegrityViolationException exception) {
            if (isRecordCodeConstraint(exception)) {
                throw conflict("Record code already exists");
            }
            throw exception;
        }
        List<RecordValue> created = new ArrayList<>();
        for (FieldDefinition field : definitions) {
            ApprovalChange change = pendingByField.get(field.getId());
            TypedValue value = change == null ? TypedValue.empty() : change.newValue();
            if (change != null) validateTyped(field, value, record.getId());
            created.add(RecordValue.create(record, field, value, actorId));
        }
        values.saveAll(created);
        return record;
    }

    private MdmRecord applyUpdate(ApprovalRequest request, MdmRecord record, Long actorId) {
        Map<Long, FieldDefinition> byId = byId(fields.findByObjectTypeId(request.getObjectTypeId()).stream()
            .filter(field -> "active".equals(field.getStatus())).toList(), FieldDefinition::getId);
        for (ApprovalChange change : changes.findByApprovalRequestId(request.getId())) {
            FieldDefinition field = requiredField(byId, change.getFieldDefinitionId());
            Optional<RecordValue> existing = values.findByRecordIdAndFieldDefinitionId(record.getId(), field.getId());
            if (existing.isEmpty() && !TypedValue.empty().sameValueAs(change.oldValue())) {
                throw conflict("Record value row is missing");
            }
            if (existing.isPresent() && !existing.get().typedValue().sameValueAs(change.oldValue())) {
                throw conflict("Record value changed after submission");
            }
            validateTyped(field, change.newValue(), record.getId());
            if (existing.isPresent()) {
                existing.get().apply(change.newValue(), actorId);
                values.save(existing.get());
            } else {
                values.save(RecordValue.create(record, field, change.newValue(), actorId));
            }
        }
        record.touch(actorId);
        return records.saveAndFlush(record);
    }

    private void applyChildren(ApprovalRequest request, MdmRecord parent,
                               List<ApprovalChildChange> requestedChildren,
                               Map<Long, ChildRecord> lockedChildren, Long actorId) {
        for (ApprovalChildChange change : requestedChildren) {
            ChildType childType = childTypes.findById(change.getChildTypeId())
                .orElseThrow(() -> conflict("Approval child type no longer exists"));
            if (!"active".equals(childType.getStatus())) throw conflict("Approval child type is inactive");
            List<ChildFieldDefinition> definitions = childFields.findByChildTypeId(childType.getId()).stream()
                .filter(field -> "active".equals(field.getStatus())).toList();
            Map<Long, ChildFieldDefinition> byId = byId(definitions, ChildFieldDefinition::getId);
            List<ApprovalChildValueChange> pending = childValueChanges
                .findByApprovalChildChangeId(change.getId());
            switch (change.getOperation()) {
                case CREATE -> applyChildCreate(parent, childType, change, byId, pending, actorId);
                case UPDATE -> applyChildUpdate(lockedChildren.get(change.getChildRecordId()), byId, pending, actorId);
                case DELETE -> applyChildDelete(lockedChildren.get(change.getChildRecordId()), byId, pending,
                    actorId);
            }
        }
    }

    private void applyChildCreate(MdmRecord parent, ChildType childType, ApprovalChildChange change,
                                  Map<Long, ChildFieldDefinition> byId,
                                  List<ApprovalChildValueChange> pending, Long actorId) {
        Map<Long, ApprovalChildValueChange> pendingByField = byId(pending,
            ApprovalChildValueChange::getFieldDefinitionId);
        if (!byId.keySet().containsAll(pendingByField.keySet())) {
            throw conflict("Approval child field set no longer matches metadata");
        }
        ChildRecord child = childRecords.saveAndFlush(ChildRecord.create(parent, childType,
            change.getSortOrder(), actorId));
        List<ChildRecordValue> created = new ArrayList<>();
        for (ChildFieldDefinition field : byId.values()) {
            ApprovalChildValueChange pendingValue = pendingByField.get(field.getId());
            TypedValue value = pendingValue == null ? TypedValue.empty() : pendingValue.newValue();
            if (pendingValue != null) validateChildTyped(field, value, child.getId());
            created.add(ChildRecordValue.create(child, field, value, actorId));
        }
        childValues.saveAll(created);
    }

    private void applyChildUpdate(ChildRecord child, Map<Long, ChildFieldDefinition> byId,
                                  List<ApprovalChildValueChange> pending, Long actorId) {
        if (child == null) throw new BusinessException(404, "Child record not found");
        for (ApprovalChildValueChange value : pending) {
            ChildFieldDefinition field = requiredChildField(byId, value.getFieldDefinitionId());
            Optional<ChildRecordValue> existing = childValues.findByChildRecordIdAndFieldDefinitionId(
                child.getId(), field.getId());
            if (existing.isEmpty() && !TypedValue.empty().sameValueAs(value.oldValue())) {
                throw conflict("Child record value row is missing");
            }
            if (existing.isPresent() && !existing.get().typedValue().sameValueAs(value.oldValue())) {
                throw conflict("Child value changed after submission");
            }
            validateChildTyped(field, value.newValue(), child.getId());
            if (existing.isPresent()) {
                existing.get().apply(value.newValue(), actorId);
                childValues.save(existing.get());
            } else {
                childValues.save(ChildRecordValue.create(child, field, value.newValue(), actorId));
            }
        }
        child.touch(actorId);
        childRecords.saveAndFlush(child);
    }

    private void applyChildDelete(ChildRecord child, Map<Long, ChildFieldDefinition> byId,
                                  List<ApprovalChildValueChange> pending, Long actorId) {
        if (child == null) throw new BusinessException(404, "Child record not found");
        Map<Long, ChildRecordValue> currentValues = Optional.ofNullable(
                childValues.findByChildRecordIdIn(List.of(child.getId())))
            .orElse(List.of()).stream().collect(Collectors.toMap(
                ChildRecordValue::getFieldDefinitionId, Function.identity()));
        Set<Long> pendingFieldIds = pending.stream().map(ApprovalChildValueChange::getFieldDefinitionId)
            .collect(Collectors.toSet());
        if (!currentValues.keySet().equals(pendingFieldIds)) {
            throw conflict("Approval child delete field set no longer matches the effective record");
        }
        for (ApprovalChildValueChange value : pending) {
            ChildFieldDefinition field = requiredChildField(byId, value.getFieldDefinitionId());
            ChildRecordValue current = currentValues.get(field.getId());
            if (current == null || !current.typedValue().sameValueAs(value.oldValue())) {
                throw conflict("Child value changed after submission");
            }
            if (!TypedValue.empty().equals(value.newValue())) {
                throw conflict("Child delete approval must clear every value");
            }
            validateChildValueAgainstCurrentMetadata(field, value.oldValue());
        }
        child.softDelete(actorId);
        childRecords.saveAndFlush(child);
    }

    private void validateTyped(FieldDefinition field, TypedValue value, Long excludedRecordId) {
        requireCurrentTypedValue(field, value);
        validateReference(field.getSystemId(), field.getReferenceObjectTypeId(), value);
        if (!field.isUniqueValue() || value.nonNullValueCount() == 0) return;
        boolean duplicate = Optional.ofNullable(values.findActiveByFieldDefinitionIdForUpdate(field.getId()))
            .orElse(List.of())
            .stream().anyMatch(existing -> !Objects.equals(excludedRecordId, existing.getRecordId())
                && existing.typedValue().sameValueAs(value));
        if (duplicate) throw conflict("Duplicate value for unique field");
    }

    private void validateChildTyped(ChildFieldDefinition field, TypedValue value, Long excludedChildId) {
        validateChildValueAgainstCurrentMetadata(field, value);
        if (!field.isUniqueValue() || value.nonNullValueCount() == 0) return;
        boolean duplicate = Optional.ofNullable(childValues.findActiveByFieldDefinitionIdForUpdate(field.getId()))
            .orElse(List.of())
            .stream().anyMatch(existing -> !Objects.equals(excludedChildId, existing.getChildRecordId())
                && existing.typedValue().sameValueAs(value));
        if (duplicate) throw conflict("Duplicate value for unique child field");
    }

    private void validateChildValueAgainstCurrentMetadata(ChildFieldDefinition field, TypedValue value) {
        FieldDefinition adapter = FieldDefinition.create(field.getChildTypeId(),
            ObjectType.create(field.getSystemId(), "CHILD_VALUE", "Child value"),
            new CreateFieldCommand(field.getFieldKey(), field.getFieldKey(), field.getDataType(), field.isRequired(),
                field.isUniqueValue(), false, field.isShared(), field.getMaxLength(), field.getPrecision(),
                field.getScale(), field.getReferenceObjectTypeId(), null, null, 0), null);
        requireCurrentTypedValue(adapter, value);
        validateReference(field.getSystemId(), field.getReferenceObjectTypeId(), value);
    }

    private void requireCurrentTypedValue(FieldDefinition field, TypedValue value) {
        try {
            TypedValue normalized = converter.convert(field, rawValue(field, value));
            if (!normalized.sameValueAs(value)) throw conflict("Approval value no longer matches current metadata");
        } catch (BusinessException exception) {
            if (exception.getCode() == 409) throw exception;
            throw conflict("Approval value no longer matches current metadata");
        }
    }

    private Object rawValue(FieldDefinition field, TypedValue value) {
        return switch (field.getDataType()) {
            case STRING -> value.stringValue();
            case TEXT -> value.textValue();
            case INTEGER -> value.integerValue();
            case DECIMAL -> value.decimalValue();
            case BOOLEAN -> value.booleanValue();
            case DATE -> value.dateValue();
            case DATETIME -> value.datetimeValue();
            case REFERENCE -> value.referenceRecordId() == null ? null
                : new TypedValueConverter.ReferenceValue(value.referenceRecordId(),
                    field.getReferenceObjectTypeId(), field.getSystemId());
        };
    }

    private void validateReference(Long systemId, Long referenceObjectTypeId, TypedValue value) {
        if (value.referenceRecordId() == null) return;
        records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(
                systemId, referenceObjectTypeId, value.referenceRecordId())
            .orElseThrow(() -> conflict("Referenced record no longer exists"));
    }

    private <T> Map<Long, T> byId(Collection<T> source, Function<T, Long> id) {
        return source.stream().collect(Collectors.toMap(id, Function.identity(), (left, right) -> left,
            LinkedHashMap::new));
    }

    private FieldDefinition requiredField(Map<Long, FieldDefinition> byId, Long id) {
        FieldDefinition field = byId.get(id);
        if (field == null) throw conflict("Approval field no longer exists");
        return field;
    }

    private ChildFieldDefinition requiredChildField(Map<Long, ChildFieldDefinition> byId, Long id) {
        ChildFieldDefinition field = byId.get(id);
        if (field == null) throw conflict("Approval child field no longer exists");
        return field;
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

    private RecordView view(MdmRecord record) {
        return new RecordView(record.getId(), record.getSystemId(), record.getObjectTypeId(),
            record.getDepartmentId(), record.getRecordCode(), record.getVersion());
    }

    private boolean isRecordCodeConstraint(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (containsRecordCodeConstraint(current.getMessage())) return true;
            if (current instanceof ConstraintViolationException constraint
                && containsRecordCodeConstraint(constraint.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsRecordCodeConstraint(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("uk_record_code");
    }

    private BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
