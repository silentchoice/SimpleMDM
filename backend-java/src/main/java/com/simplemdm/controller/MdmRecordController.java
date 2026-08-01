package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.mdm.CreateRecordRequest;
import com.simplemdm.dto.mdm.MasterChildChangeRequest;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.service.mdm.RecordProjectionService;
import com.simplemdm.service.mdm.RecordService;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.system.RecordAccessService;
import com.simplemdm.service.workflow.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

@RestController
@RequestMapping("/api/mdm")
public class MdmRecordController {
    private final RecordService recordService;
    private final ObjectTypeRepository objectTypes;
    private final MdmRecordRepository records;
    private final ChildTypeRepository childTypes;
    private final ChildRecordRepository childRecords;
    private final AuthorizationService authorization;
    private final RecordAccessService access;
    private final RecordProjectionService projection;
    private final ApprovalService approvals;

    public MdmRecordController(RecordService recordService, ObjectTypeRepository objectTypes,
                               MdmRecordRepository records, ChildTypeRepository childTypes,
                               ChildRecordRepository childRecords, AuthorizationService authorization,
                               RecordAccessService access, RecordProjectionService projection,
                               ApprovalService approvals) {
        this.recordService = recordService;
        this.objectTypes = objectTypes;
        this.records = records;
        this.childTypes = childTypes;
        this.childRecords = childRecords;
        this.authorization = authorization;
        this.access = access;
        this.projection = projection;
        this.approvals = approvals;
    }

    @GetMapping("/object-types/{objectCode}/records")
    public ApiResponse list(@PathVariable String objectCode) {
        User user = SystemController.currentUser();
        ObjectType objectType = requiredObjectType(user, objectCode);
        RecordAccessService.Snapshot snapshot = access.snapshot(user);
        Set<Long> visibleDepartments = snapshot.readableDepartmentIds();
        if (visibleDepartments.isEmpty()) return ApiResponse.ok(List.of());
        List<MdmRecord> matching = records.findBySystemIdAndObjectTypeIdAndDepartmentIdIn(
            user.getSystemId(), objectType.getId(), visibleDepartments);
        Set<Long> distributableRecordIds = matching.stream().filter(MdmRecord::isActive)
            .map(MdmRecord::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long, Boolean> distributionByDepartment = new HashMap<>();
        return ApiResponse.ok(projection.records(snapshot, objectCode, objectType.getId(), matching).stream()
            .map(record -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", record.id());
                value.put("object_type", record.objectType());
                value.put("department_id", record.departmentId());
                value.put("record_code", record.recordCode());
                value.put("status", record.status());
                value.put("version", record.version());
                value.put("data", record.data());
                value.put("can_distribute", distributableRecordIds.contains(record.id())
                    && (user.isSystemAdmin() || distributionByDepartment.computeIfAbsent(record.departmentId(),
                        departmentId -> authorization.can(user.getId(), "INTEGRATION_MANUAL_PUSH", departmentId))));
                return value;
            }).toList());
    }

    @GetMapping("/object-types/{objectCode}/records/{recordId}")
    public ApiResponse detail(@PathVariable String objectCode, @PathVariable Long recordId) {
        User user = SystemController.currentUser();
        ObjectType objectType = requiredObjectType(user, objectCode);
        MdmRecord record = records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(
                user.getSystemId(), objectType.getId(), recordId)
            .filter(MdmRecord::isActive)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        RecordAccessService.Snapshot snapshot = access.snapshot(user);
        if (snapshot.decision(record.getDepartmentId()) == RecordAccessService.Decision.DENY) {
            throw new BusinessException(404, "Record not found");
        }
        var projected = projection.records(snapshot, objectCode, objectType.getId(), List.of(record)).stream()
            .findFirst().orElseThrow(() -> new BusinessException(404, "Record not found"));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", projected.id());
        value.put("object_type", projected.objectType());
        value.put("department_id", projected.departmentId());
        value.put("record_code", projected.recordCode());
        value.put("status", projected.status());
        value.put("version", projected.version());
        value.put("data", projected.data());
        value.put("can_distribute", user.isSystemAdmin()
            || authorization.can(user.getId(), "INTEGRATION_MANUAL_PUSH", projected.departmentId()));
        return ApiResponse.ok(value);
    }

    @PostMapping("/object-types/{objectCode}/records")
    public ApiResponse create(@PathVariable String objectCode,
                              @Valid @RequestBody MasterChildChangeRequest request) {
        User user = SystemController.currentUser();
        requiredObjectType(user, objectCode);
        requireRoute(request, objectCode, MasterChildChangeRequest.Operation.CREATE);
        return pending(approvals.submit(request, user.getId()));
    }

    @PutMapping("/object-types/{objectCode}/records")
    public ApiResponse update(@PathVariable String objectCode,
                              @Valid @RequestBody MasterChildChangeRequest request) {
        User user = SystemController.currentUser();
        requiredObjectType(user, objectCode);
        requireRoute(request, objectCode, MasterChildChangeRequest.Operation.UPDATE);
        return pending(approvals.submit(request, user.getId()));
    }

    @GetMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse children(@PathVariable Long recordId, @PathVariable String childCode) {
        User user = SystemController.currentUser();
        MdmRecord parent = records.findBySystemIdAndId(user.getSystemId(), recordId)
            .filter(MdmRecord::isActive)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
        RecordAccessService.Snapshot snapshot = access.snapshot(user);
        if (snapshot.decision(parent.getDepartmentId()) == RecordAccessService.Decision.DENY) {
            throw new BusinessException(404, "Record not found");
        }
        ChildType childType = childTypes.findBySystemIdAndObjectTypeIdAndCode(
                user.getSystemId(), parent.getObjectTypeId(), childCode)
            .filter(type -> "active".equals(type.getStatus()))
            .orElseThrow(() -> new BusinessException(404, "Child type not found"));
        return ApiResponse.ok(projection.children(snapshot, user, parent, childCode, childType));
    }

    @PostMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse createChild(@PathVariable Long recordId, @PathVariable String childCode,
                                   @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        MdmRecord parent = requiredParent(user, recordId);
        requiredChildType(user, parent.getObjectTypeId(), childCode);
        if (request == null || request.data() == null) throw new BusinessException(400, "Child record data is required");
        MasterChildChangeRequest approval = childRequest(parent, childCode,
            new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.CREATE,
                null, null, request.data()));
        return pending(approvals.submit(approval, user.getId()));
    }

    @PutMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse updateChild(@PathVariable Long recordId, @PathVariable String childCode,
                                   @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        MdmRecord parent = requiredParent(user, recordId);
        if (request == null || request.id() == null || request.version() == null || request.data() == null) {
            throw new BusinessException(400, "Child record ID, version, and data are required");
        }
        ChildType childType = requiredChildType(user, parent.getObjectTypeId(), childCode);
        requireChildTarget(user.getSystemId(), recordId, childType.getId(), request.id());
        MasterChildChangeRequest approval = childRequest(parent, childCode,
            new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.UPDATE,
                request.id(), request.version(), request.data()));
        return pending(approvals.submit(approval, user.getId()));
    }

    @DeleteMapping("/records/{recordId}/children/{childCode}")
    public ApiResponse deleteChild(@PathVariable Long recordId, @PathVariable String childCode,
                                   @RequestBody CreateRecordRequest request) {
        User user = SystemController.currentUser();
        MdmRecord parent = requiredParent(user, recordId);
        ChildType childType = requiredChildType(user, parent.getObjectTypeId(), childCode);
        if (request == null || request.id() == null || request.version() == null) {
            throw new BusinessException(400, "Child record ID and version are required");
        }
        requireChildTarget(user.getSystemId(), recordId, childType.getId(), request.id());
        MasterChildChangeRequest approval = childRequest(parent, childCode,
            new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.DELETE,
                request.id(), request.version(), Map.of()));
        return pending(approvals.submit(approval, user.getId()));
    }

    private MasterChildChangeRequest childRequest(MdmRecord parent, String childCode,
                                                   MasterChildChangeRequest.ChildRow row) {
        ObjectType objectType = objectTypes.findById(parent.getObjectTypeId())
            .orElseThrow(() -> new BusinessException(409, "Parent object type is missing"));
        return new MasterChildChangeRequest(MasterChildChangeRequest.Operation.UPDATE, objectType.getCode(),
            parent.getId(), parent.getVersion(), parent.getRecordCode(), parent.getDepartmentId(), Map.of(),
            List.of(new MasterChildChangeRequest.ChildGroup(childCode, List.of(row))));
    }

    private MdmRecord requiredParent(User user, Long recordId) {
        return records.findBySystemIdAndId(user.getSystemId(), recordId)
            .filter(MdmRecord::isActive)
            .orElseThrow(() -> new BusinessException(404, "Record not found"));
    }

    private void requireChildTarget(Long systemId, Long recordId, Long childTypeId, Long childId) {
        childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(
                systemId, recordId, childTypeId, childId)
            .orElseThrow(() -> new BusinessException(404, "Child record not found"));
    }

    private void requireRoute(MasterChildChangeRequest request, String objectCode,
                              MasterChildChangeRequest.Operation operation) {
        if (request == null || request.operation() != operation || !objectCode.equals(request.objectCode())) {
            throw new BusinessException(400, "Approval operation and object code must match the route");
        }
    }

    private ApiResponse pending(Long id) {
        return ApiResponse.ok(Map.of("id", id, "status", "PENDING"));
    }

    private ObjectType requiredObjectType(User user, String objectCode) {
        return objectTypes.findBySystemIdAndCode(user.getSystemId(), objectCode)
            .filter(ObjectType::isActive)
            .orElseThrow(() -> new BusinessException(404, "Object type not found"));
    }

    private ChildType requiredChildType(User user, Long objectTypeId, String childCode) {
        return childTypes.findBySystemIdAndObjectTypeIdAndCode(user.getSystemId(), objectTypeId, childCode)
            .filter(type -> "active".equals(type.getStatus()))
            .orElseThrow(() -> new BusinessException(404, "Child type not found"));
    }
}
