package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.mdm.CreateRecordRequest;
import com.simplemdm.dto.mdm.RecordResponse;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.service.mdm.ChildRecordView;
import com.simplemdm.service.mdm.CreateChildRecordCommand;
import com.simplemdm.service.mdm.CreateRecordCommand;
import com.simplemdm.service.mdm.RecordService;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mdm")
public class MdmRecordController {
    private final RecordService recordService;
    private final ObjectTypeRepository objectTypes;
    private final MdmRecordRepository records;
    private final FieldDefinitionRepository fields;
    private final RecordValueRepository values;
    private final ChildTypeRepository childTypes;
    private final ChildRecordRepository childRecords;
    private final ChildFieldDefinitionRepository childFields;
    private final ChildRecordValueRepository childValues;
    private final AuthorizationService authorization;

    public MdmRecordController(RecordService recordService, ObjectTypeRepository objectTypes,
                               MdmRecordRepository records, FieldDefinitionRepository fields,
                               RecordValueRepository values, ChildTypeRepository childTypes,
                               ChildRecordRepository childRecords, ChildFieldDefinitionRepository childFields,
                               ChildRecordValueRepository childValues, AuthorizationService authorization) {
        this.recordService = recordService;
        this.objectTypes = objectTypes;
        this.records = records;
        this.fields = fields;
        this.values = values;
        this.childTypes = childTypes;
        this.childRecords = childRecords;
        this.childFields = childFields;
        this.childValues = childValues;
        this.authorization = authorization;
    }

    @GetMapping("/object-types/{objectCode}/records")
    public ApiResponse list(@PathVariable String objectCode) {
        User user = SystemController.currentUser();
        ObjectType objectType = requiredObjectType(user, objectCode);
        Set<Long> visibleDepartments = authorization.viewableDepartmentIds(user.getId());
        if (visibleDepartments.isEmpty()) return ApiResponse.ok(List.of());
        List<MdmRecord> matching = records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(
            user.getSystemId(), objectType.getId(), visibleDepartments);
        return ApiResponse.ok(assemble(objectCode, objectType.getId(), matching));
    }

    @PostMapping("/object-types/{objectCode}/records")
    public ApiResponse create(@PathVariable String objectCode, @Validated(CreateRecordRequest.Create.class) @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        ObjectType objectType = requiredObjectType(user, objectCode);
        requireCreateRequest(request);
        RecordView created = recordService.create(new CreateRecordCommand(user.getSystemId(), objectType.getId(),
            request.departmentId(), request.recordCode(), request.data()));
        return ApiResponse.ok(response(created, objectCode, "active", request.data()));
    }

    @PutMapping("/object-types/{objectCode}/records")
    public ApiResponse update(@PathVariable String objectCode, @Validated(CreateRecordRequest.Update.class) @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        ObjectType routeType = requiredObjectType(user, objectCode);
        if (request == null || request.id() == null || request.version() == null || request.data() == null) {
            throw new BusinessException(400, "Record ID, version, and data are required");
        }
        MdmRecord persisted = records.findBySystemIdAndId(user.getSystemId(), request.id())
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (!routeType.getId().equals(persisted.getObjectTypeId())) throw new BusinessException(404, "Record not found");
        RecordView updated = recordService.update(request.id(), request.version(), request.data());
        return ApiResponse.ok(response(updated, objectCode, persisted.getStatus(), request.data()));
    }

    @GetMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse children(@PathVariable Long recordId, @PathVariable String childCode) {
        User user = SystemController.currentUser();
        MdmRecord parent = records.findBySystemIdAndId(user.getSystemId(), recordId)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (!authorization.can(user.getId(), "MDM_RECORD_VIEW", parent.getDepartmentId())) throw new BusinessException(403, "User is not authorized to view this department");
        ChildType childType = childTypes.findBySystemIdAndObjectTypeIdAndCode(user.getSystemId(), parent.getObjectTypeId(), childCode)
            .orElseThrow(() -> new BusinessException(404, "Child type not found"));
        List<com.simplemdm.model.mdm.ChildRecord> children = childRecords.findBySystemIdAndRecordIdAndChildTypeId(user.getSystemId(), recordId, childType.getId());
        Map<Long, ChildFieldDefinition> definitions = childFields.findByChildTypeId(childType.getId()).stream()
            .collect(Collectors.toMap(ChildFieldDefinition::getId, Function.identity()));
        Map<Long, Map<String,Object>> data = new HashMap<>();
        for (var child : children) data.put(child.getId(), new HashMap<>());
        for (var value : childValues.findByChildRecordIdIn(children.stream().map(c -> c.getId()).toList())) {
            ChildFieldDefinition field = definitions.get(value.getFieldDefinitionId());
            if (field != null) data.get(value.getChildRecordId()).put(field.getFieldKey(), untyped(value.typedValue()));
        }
        return ApiResponse.ok(children.stream().map(child -> Map.<String,Object>of("id", child.getId(), "parent_record_id", recordId,
            "child_type", childCode, "department_id", parent.getDepartmentId(), "version", child.getVersion(), "data", data.get(child.getId()))).toList());
    }
    @PostMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse createChild(@PathVariable Long recordId, @PathVariable String childCode,
                                   @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        MdmRecord parent = records.findBySystemIdAndId(user.getSystemId(), recordId)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (!user.getSystemId().equals(parent.getSystemId())) throw new BusinessException(404, "Record not found");
        ChildType childType = requiredChildType(user, parent.getObjectTypeId(), childCode);
        if (request == null || request.data() == null) throw new BusinessException(400, "Child record data is required");
        ChildRecordView created = recordService.createChild(new CreateChildRecordCommand(recordId, childType.getId(), request.data()));
        return ApiResponse.ok(Map.of("id", created.id(), "parent_record_id", created.parentRecordId(),
            "child_type", childCode, "department_id", created.departmentId(), "version", created.version(), "data", request.data()));
    }

    @PutMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse updateChild(@PathVariable Long recordId, @PathVariable String childCode,
                                   @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        MdmRecord parent = records.findBySystemIdAndId(user.getSystemId(), recordId)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        if (!user.getSystemId().equals(parent.getSystemId())
            || !authorization.can(user.getId(), "MDM_RECORD_EDIT", parent.getDepartmentId())) {
            throw new BusinessException(403, "User is not authorized to edit this department");
        }
        ChildType childType = requiredChildType(user, parent.getObjectTypeId(), childCode);
        if (request == null || request.id() == null || request.data() == null) {
            throw new BusinessException(400, "Child record ID and data are required");
        }
        com.simplemdm.model.mdm.ChildRecord child = childRecords.findById(request.id())
            .orElseThrow(() -> new BusinessException(404, "Child record not found"));
        if (!recordId.equals(child.getRecordId()) || !childType.getId().equals(child.getChildTypeId())) {
            throw new BusinessException(404, "Child record not found");
        }
        ChildRecordView updated = recordService.updateChild(request.id(), request.version(), request.data());
        return ApiResponse.ok(Map.of("id", updated.id(), "parent_record_id", updated.parentRecordId(),
            "child_type", childCode, "department_id", updated.departmentId(), "version", updated.version(), "data", request.data()));
    }

    private List<RecordResponse> assemble(String objectCode, Long objectTypeId, List<MdmRecord> matching) {
        if (matching.isEmpty()) return List.of();
        Map<Long, FieldDefinition> byFieldId = fields.findByObjectTypeId(objectTypeId).stream()
            .collect(Collectors.toMap(FieldDefinition::getId, Function.identity()));
        Map<Long, Map<String, Object>> data = new HashMap<>();
        for (MdmRecord record : matching) data.put(record.getId(), new HashMap<>());
        for (RecordValue value : values.findByRecordIdIn(matching.stream().map(MdmRecord::getId).toList())) {
            FieldDefinition field = byFieldId.get(value.getFieldDefinitionId());
            if (field != null) data.get(value.getRecordId()).put(field.getFieldKey(), untyped(value.typedValue()));
        }
        return matching.stream().map(record -> new RecordResponse(record.getId(), objectCode, record.getDepartmentId(),
            record.getRecordCode(), record.getStatus(), record.getVersion(), data.get(record.getId()))).toList();
    }

    private RecordResponse response(RecordView view, String objectCode, String status, Map<String, Object> data) {
        return new RecordResponse(view.id(), objectCode, view.departmentId(), view.recordCode(), status, view.version(), data);
    }

    private ObjectType requiredObjectType(User user, String objectCode) {
        return objectTypes.findBySystemIdAndCode(user.getSystemId(), objectCode)
            .orElseThrow(() -> new BusinessException(404, "Object type not found"));
    }

    private ChildType requiredChildType(User user, Long objectTypeId, String childCode) {
        return childTypes.findAll().stream()
            .filter(type -> user.getSystemId().equals(type.getSystemId()) && objectTypeId.equals(type.getObjectTypeId())
                && childCode.equals(type.getCode()))
            .findFirst().orElseThrow(() -> new BusinessException(404, "Child type not found"));
    }

    private void requireCreateRequest(CreateRecordRequest request) {
        if (request == null || request.departmentId() == null || request.recordCode() == null || request.recordCode().isBlank()
            || request.data() == null) throw new BusinessException(400, "Department, record code, and data are required");
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