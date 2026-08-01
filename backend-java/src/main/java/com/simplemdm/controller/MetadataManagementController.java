package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.mdm.MetadataCommands;
import com.simplemdm.model.mdm.*;
import com.simplemdm.service.mdm.MetadataManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mdm/object-types/{code}")
public class MetadataManagementController {
    private final MetadataManagementService metadata;
    public MetadataManagementController(MetadataManagementService metadata) { this.metadata = metadata; }
    @PatchMapping public ApiResponse updateObjectType(@PathVariable String code,
            @Valid @RequestBody MetadataCommands.UpdateObjectType command) {
        return objectTypeResponse(metadata.updateObjectType(SystemController.currentUser(), code, command)); }
    @PostMapping("/deactivate") public ApiResponse deactivateObjectType(@PathVariable String code) {
        return objectTypeResponse(metadata.deactivateObjectType(SystemController.currentUser(), code)); }
    @PostMapping("/reactivate") public ApiResponse reactivateObjectType(@PathVariable String code) {
        return objectTypeResponse(metadata.reactivateObjectType(SystemController.currentUser(), code)); }
    @PostMapping("/fields") public ApiResponse create(@PathVariable String code, @Valid @RequestBody MetadataCommands.CreateField command) {
        return response(metadata.createMasterField(SystemController.currentUser(), code, command)); }
    @PatchMapping("/fields/{id}") public ApiResponse update(@PathVariable String code, @PathVariable Long id,
                                                        @Valid @RequestBody MetadataCommands.UpdateField command) {
        return response(metadata.updateMasterField(SystemController.currentUser(), code, id, command)); }
    @PostMapping("/fields/{id}/deactivate") public ApiResponse deactivate(@PathVariable String code, @PathVariable Long id) {
        return response(metadata.deactivateMasterField(SystemController.currentUser(), code, id)); }
    @PostMapping("/fields/{id}/reactivate") public ApiResponse reactivate(@PathVariable String code, @PathVariable Long id) {
        return response(metadata.reactivateMasterField(SystemController.currentUser(), code, id)); }
    @PostMapping("/child-types") public ApiResponse createChildType(@PathVariable String code,
            @Valid @RequestBody MetadataCommands.CreateChildType command) {
        var mutation = metadata.createChildType(SystemController.currentUser(), code, command);
        return ApiResponse.ok(Map.of("child_type", childType(mutation.childType()), "audit_id", mutation.auditId()));
    }
    @PatchMapping("/child-types/{id}") public ApiResponse updateChildType(@PathVariable String code, @PathVariable Long id,
            @Valid @RequestBody MetadataCommands.UpdateChildType command) {
        var mutation = metadata.updateChildType(SystemController.currentUser(), code, id, command);
        return ApiResponse.ok(Map.of("child_type", childType(mutation.childType()), "audit_id", mutation.auditId()));
    }
    @PostMapping("/child-types/{id}/deactivate") public ApiResponse deactivateChildType(@PathVariable String code, @PathVariable Long id) {
        var mutation = metadata.deactivateChildType(SystemController.currentUser(), code, id);
        return ApiResponse.ok(Map.of("child_type", childType(mutation.childType()), "audit_id", mutation.auditId()));
    }
    @PostMapping("/child-types/{id}/reactivate") public ApiResponse reactivateChildType(@PathVariable String code, @PathVariable Long id) {
        var mutation = metadata.reactivateChildType(SystemController.currentUser(), code, id);
        return ApiResponse.ok(Map.of("child_type", childType(mutation.childType()), "audit_id", mutation.auditId()));
    }
    @PostMapping("/child-types/{childTypeId}/fields") public ApiResponse createChildField(@PathVariable String code,
            @PathVariable Long childTypeId, @Valid @RequestBody MetadataCommands.CreateChildField command) {
        return childFieldResponse(metadata.createChildField(SystemController.currentUser(), code, childTypeId, command));
    }
    @PatchMapping("/child-types/{childTypeId}/fields/{id}") public ApiResponse updateChildField(@PathVariable String code,
            @PathVariable Long childTypeId, @PathVariable Long id, @Valid @RequestBody MetadataCommands.UpdateChildField command) {
        return childFieldResponse(metadata.updateChildField(SystemController.currentUser(), code, childTypeId, id, command));
    }
    @PostMapping("/child-types/{childTypeId}/fields/{id}/deactivate") public ApiResponse deactivateChildField(
            @PathVariable String code, @PathVariable Long childTypeId, @PathVariable Long id) {
        return childFieldResponse(metadata.deactivateChildField(SystemController.currentUser(), code, childTypeId, id));
    }
    @PostMapping("/child-types/{childTypeId}/fields/{id}/reactivate") public ApiResponse reactivateChildField(
            @PathVariable String code, @PathVariable Long childTypeId, @PathVariable Long id) {
        return childFieldResponse(metadata.reactivateChildField(SystemController.currentUser(), code, childTypeId, id));
    }
    private ApiResponse response(MetadataManagementService.FieldMutation mutation) {
        return ApiResponse.ok(Map.of("field", field(mutation.field()), "audit_id", mutation.auditId()));
    }
    private ApiResponse objectTypeResponse(MetadataManagementService.ObjectTypeMutation mutation) {
        return ApiResponse.ok(Map.of("object_type", objectType(mutation.objectType()), "audit_id", mutation.auditId()));
    }
    private Map<String, Object> objectType(ObjectType value) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", value.getId()); result.put("code", value.getCode());
        result.put("name", value.getName()); result.put("approval_required", value.isApprovalRequired());
        result.put("department_scoped", value.isDepartmentScoped()); result.put("status", value.getStatus()); return result;
    }
    private Map<String, Object> field(FieldDefinition f) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", f.getId()); result.put("field_key", f.getFieldKey());
        result.put("field_name", f.getFieldName()); result.put("data_type", f.getDataType().name()); result.put("required", f.isRequired());
        result.put("unique_value", f.isUniqueValue()); result.put("searchable", f.isSearchable()); result.put("shared", f.isShared());
        result.put("max_length", f.getMaxLength()); result.put("precision_value", f.getPrecision()); result.put("scale_value", f.getScale());
        result.put("reference_object_type_id", f.getReferenceObjectTypeId()); result.put("default_value", f.getDefaultValue());
        result.put("validation_rule", f.getValidationRule()); result.put("sort_order", f.getSortOrder()); result.put("status", f.getStatus()); return result;
    }
    private ApiResponse childFieldResponse(MetadataManagementService.ChildFieldMutation mutation) {
        return ApiResponse.ok(Map.of("field", childField(mutation.field()), "audit_id", mutation.auditId()));
    }
    private Map<String, Object> childType(ChildType value) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", value.getId()); result.put("code", value.getCode());
        result.put("name", value.getName()); result.put("sort_order", value.getSortOrder()); result.put("status", value.getStatus()); return result;
    }
    private Map<String, Object> childField(ChildFieldDefinition f) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", f.getId()); result.put("child_type_id", f.getChildTypeId());
        result.put("field_key", f.getFieldKey()); result.put("field_name", f.getFieldName()); result.put("data_type", f.getDataType().name());
        result.put("required", f.isRequired()); result.put("unique_value", f.isUniqueValue()); result.put("searchable", f.isSearchable());
        result.put("shared", f.isShared()); result.put("max_length", f.getMaxLength()); result.put("precision_value", f.getPrecision());
        result.put("scale_value", f.getScale()); result.put("reference_object_type_id", f.getReferenceObjectTypeId());
        result.put("default_value", f.getDefaultValue()); result.put("validation_rule", f.getValidationRule());
        result.put("sort_order", f.getSortOrder()); result.put("status", f.getStatus()); return result;
    }
}
