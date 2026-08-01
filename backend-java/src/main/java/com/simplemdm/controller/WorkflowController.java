package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.mdm.MasterChildChangeRequest;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalChildChange;
import com.simplemdm.model.workflow.ApprovalChildValueChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.system.RecordAccessService;
import com.simplemdm.service.workflow.ApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow/approvals")
public class WorkflowController {
    private final ApprovalRequestRepository requests;
    private final ApprovalChangeRepository changes;
    private final ApprovalChildChangeRepository childChanges;
    private final ApprovalChildValueChangeRepository childValueChanges;
    private final FieldDefinitionRepository fields;
    private final ChildFieldDefinitionRepository childFields;
    private final ChildTypeRepository childTypes;
    private final ApprovalService service;
    private final RecordAccessService recordAccess;

    public WorkflowController(ApprovalRequestRepository requests, ApprovalChangeRepository changes,
                              ApprovalChildChangeRepository childChanges,
                              ApprovalChildValueChangeRepository childValueChanges,
                              FieldDefinitionRepository fields, ChildFieldDefinitionRepository childFields,
                              ChildTypeRepository childTypes,
                              ApprovalService service, RecordAccessService recordAccess) {
        this.requests = requests;
        this.changes = changes;
        this.childChanges = childChanges;
        this.childValueChanges = childValueChanges;
        this.fields = fields;
        this.childFields = childFields;
        this.childTypes = childTypes;
        this.service = service;
        this.recordAccess = recordAccess;
    }

    @GetMapping
    public ApiResponse list() {
        User user = user();
        RecordAccessService.Snapshot access = recordAccess.snapshot(user);
        return ApiResponse.ok(requests.findBySystemIdOrderByIdDesc(user.getSystemId()).stream()
            .filter(request -> access.decision(request.getDepartmentId()) != RecordAccessService.Decision.DENY)
            .map(this::view).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse detail(@PathVariable Long id) {
        User user = user();
        ApprovalRequest request = sameSystem(id, user);
        RecordAccessService.Decision decision = recordAccess.access(user, request.getDepartmentId());
        if (decision == RecordAccessService.Decision.DENY) {
            throw new BusinessException(404, "Approval not found");
        }
        return ApiResponse.ok(detailView(request, decision, user));
    }

    @PostMapping("/submit")
    public ApiResponse submit(@Valid @RequestBody MasterChildChangeRequest body) {
        User user = user();
        Long id = service.submit(body, user.getId());
        return ApiResponse.ok(Map.of("id", id, "status", "PENDING"));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse approve(@PathVariable Long id) {
        User user = user();
        return ApiResponse.ok(service.approve(id, user.getId()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse reject(@PathVariable Long id,
                              @Valid @RequestBody(required = false) RejectionBody body) {
        User user = user();
        service.reject(id, user.getId(), body == null ? null : body.comment());
        return ApiResponse.ok(Map.of("id", id, "status", "REJECTED"));
    }

    public record RejectionBody(@Size(max = 2048) String comment) { }

    private ApprovalRequest sameSystem(Long id, User user) {
        return requests.findBySystemIdAndId(user.getSystemId(), id)
            .orElseThrow(() -> new BusinessException(404, "Approval not found"));
    }

    private User user() {
        User user = JwtInterceptor.CURRENT_USER.get();
        if (user == null) throw new BusinessException(401, "System user required");
        return user;
    }

    private Map<String, Object> view(ApprovalRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", request.getId());
        result.put("operation", request.getOperation().name());
        result.put("object_type_id", request.getObjectTypeId());
        result.put("record_id", request.getRecordId());
        result.put("record_code", request.getRecordCode());
        result.put("department_id", request.getDepartmentId());
        result.put("requested_by", request.getRequestedBy());
        result.put("expected_version", request.getExpectedVersion());
        result.put("status", request.getStatus());
        return result;
    }

    private Map<String, Object> detailView(ApprovalRequest request, RecordAccessService.Decision decision,
                                           User user) {
        Map<Long, FieldDefinition> masterDefinitions = new HashMap<>();
        List<ApprovalChange> masterChanges = changes.findByApprovalRequestId(request.getId());
        fields.findAllById(masterChanges.stream().map(ApprovalChange::getFieldDefinitionId).toList())
            .forEach(field -> masterDefinitions.put(field.getId(), field));
        Map<String, Object> result = view(request);
        result.put("changes", masterChanges.stream().map(change -> {
            FieldDefinition field = masterDefinitions.get(change.getFieldDefinitionId());
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("field_key", field == null ? "未知字段" : field.getFieldKey());
            value.put("field_name", field == null ? "未知字段" : field.getFieldName());
            value.put("data_type", field == null ? null : field.getDataType().name());
            value.put("old_value", scalar(change.oldValue()));
            value.put("new_value", scalar(change.newValue()));
            return value;
        }).toList());

        List<ApprovalChildChange> groups = childChanges
            .findByApprovalRequestIdOrderBySortOrderAscIdAsc(request.getId());
        Map<Long, ChildType> childTypeDefinitions = new HashMap<>();
        childTypes.findAllById(groups.stream().map(ApprovalChildChange::getChildTypeId).distinct().toList())
            .forEach(type -> {
                if (request.getSystemId().equals(type.getSystemId())) {
                    childTypeDefinitions.put(type.getId(), type);
                }
            });
        Map<Long, ChildFieldDefinition> childDefinitions = new HashMap<>();
        List<ApprovalChildValueChange> pendingValues;
        if (groups.isEmpty()) {
            pendingValues = List.of();
        } else if (decision == RecordAccessService.Decision.FULL) {
            pendingValues = childValueChanges.findByApprovalChildChangeIdIn(
                    groups.stream().map(ApprovalChildChange::getId).toList()).stream()
                .filter(value -> request.getSystemId().equals(value.getSystemId())).toList();
            childFields.findAllById(pendingValues.stream().map(
                    ApprovalChildValueChange::getFieldDefinitionId).toList())
                .forEach(field -> {
                    if (request.getSystemId().equals(field.getSystemId())) childDefinitions.put(field.getId(), field);
                });
        } else {
            groups.stream().map(ApprovalChildChange::getChildTypeId).distinct()
                .flatMap(childTypeId -> childFields
                    .findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(childTypeId, "active")
                    .stream())
                .filter(field -> "active".equals(field.getStatus()))
                .filter(field -> request.getSystemId().equals(field.getSystemId()))
                .forEach(field -> childDefinitions.put(field.getId(), field));
            pendingValues = childDefinitions.isEmpty() ? List.of()
                : childValueChanges.findByApprovalChildChangeIdInAndFieldDefinitionIdIn(
                    groups.stream().map(ApprovalChildChange::getId).toList(), childDefinitions.keySet());
        }
        Map<Long, List<ApprovalChildValueChange>> valuesByChange = new HashMap<>();
        for (ApprovalChildValueChange value : pendingValues) {
            valuesByChange.computeIfAbsent(value.getApprovalChildChangeId(), ignored -> new ArrayList<>()).add(value);
        }
        result.put("child_changes", groups.stream()
            .filter(group -> decision == RecordAccessService.Decision.FULL
                || !valuesByChange.getOrDefault(group.getId(), List.of()).isEmpty())
            .map(group -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("change_key", group.getChangeKey());
            value.put("child_type_id", group.getChildTypeId());
            ChildType childType = childTypeDefinitions.get(group.getChildTypeId());
            value.put("child_type_name", childType == null ? "未知子表" : childType.getName());
            value.put("child_record_id", group.getChildRecordId());
            value.put("operation", group.getOperation().name());
            value.put("expected_version", group.getExpectedVersion());
            value.put("values", valuesByChange.getOrDefault(group.getId(), List.of()).stream().map(change -> {
                ChildFieldDefinition field = childDefinitions.get(change.getFieldDefinitionId());
                Map<String, Object> fieldValue = new LinkedHashMap<>();
                fieldValue.put("field_key", field == null ? "未知字段" : field.getFieldKey());
                fieldValue.put("field_name", field == null ? "未知字段" : field.getFieldName());
                fieldValue.put("data_type", field == null ? null : field.getDataType().name());
                fieldValue.put("old_value", scalar(change.oldValue()));
                fieldValue.put("new_value", scalar(change.newValue()));
                return fieldValue;
            }).toList());
            return value;
        }).toList());
        result.put("can_approve", service.canApprove(request, user.getId()));
        return result;
    }

    private Object scalar(TypedValue value) {
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
