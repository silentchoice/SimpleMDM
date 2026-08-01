package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mdm")
public class MdmMetadataController {
    private final ObjectTypeRepository objectTypes;
    private final FieldDefinitionRepository fields;
    private final ChildTypeRepository childTypes;
    private final ChildFieldDefinitionRepository childFields;

    public MdmMetadataController(ObjectTypeRepository objectTypes, FieldDefinitionRepository fields,
                                 ChildTypeRepository childTypes, ChildFieldDefinitionRepository childFields) {
        this.objectTypes = objectTypes;
        this.fields = fields;
        this.childTypes = childTypes;
        this.childFields = childFields;
    }

    @GetMapping("/object-types")
    public ApiResponse objectTypes(@RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive) {
        Long systemId = SystemController.currentUser().getSystemId();
        var types = objectTypes.findBySystemId(systemId);
        List<Long> objectIds = types.stream().map(type -> type.getId()).toList();
        Map<Long, List<FieldDefinition>> definitions = objectIds.isEmpty() ? Map.of()
            : fields.findBySystemIdAndObjectTypeIdIn(systemId, objectIds).stream()
                .filter(field -> includeInactive || "active".equals(field.getStatus()))
                .collect(Collectors.groupingBy(FieldDefinition::getObjectTypeId));
        List<ChildType> activeChildTypes = objectIds.isEmpty() ? List.of()
            : includeInactive
                ? childTypes.findBySystemIdAndObjectTypeIdInOrderBySortOrderAscIdAsc(systemId, objectIds)
                : childTypes.findBySystemIdAndObjectTypeIdInAndStatusOrderBySortOrderAscIdAsc(systemId, objectIds, "active");
        Map<Long, List<ChildType>> childTypesByObject = activeChildTypes.stream()
            .collect(Collectors.groupingBy(ChildType::getObjectTypeId));
        List<Long> childTypeIds = activeChildTypes.stream().map(ChildType::getId).toList();
        Map<Long, List<ChildFieldDefinition>> childFieldsByType = childTypeIds.isEmpty() ? Map.of()
            : (includeInactive
                    ? childFields.findBySystemIdAndChildTypeIdInOrderBySortOrderAscIdAsc(systemId, childTypeIds)
                    : childFields.findBySystemIdAndChildTypeIdInAndStatusOrderBySortOrderAscIdAsc(
                        systemId, childTypeIds, "active")).stream()
                .collect(Collectors.groupingBy(ChildFieldDefinition::getChildTypeId));

        return ApiResponse.ok(types.stream().map(type -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", type.getId());
            item.put("code", type.getCode());
            item.put("name", type.getName());
            item.put("approval_required", type.isApprovalRequired());
            item.put("department_scoped", type.isDepartmentScoped());
            item.put("status", type.getStatus());
            item.put("fields", definitions.getOrDefault(type.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(FieldDefinition::getSortOrder)
                    .thenComparing(FieldDefinition::getId))
                .map(this::fieldResponse).toList());
            item.put("child_types", childTypesByObject.getOrDefault(type.getId(), List.of()).stream()
                .map(childType -> childTypeResponse(childType,
                    childFieldsByType.getOrDefault(childType.getId(), List.of()))).toList());
            return item;
        }).toList());
    }

    public ApiResponse objectTypes() { return objectTypes(false); }

    private Map<String, Object> childTypeResponse(ChildType type, List<ChildFieldDefinition> definitions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", type.getId());
        result.put("code", type.getCode());
        result.put("name", type.getName());
        result.put("sort_order", type.getSortOrder());
        result.put("status", type.getStatus());
        result.put("fields", definitions.stream().map(this::childFieldResponse).toList());
        return result;
    }

    private Map<String, Object> childFieldResponse(ChildFieldDefinition field) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", field.getId());
        result.put("field_key", field.getFieldKey());
        result.put("field_name", field.getFieldName());
        result.put("data_type", field.getDataType().name());
        result.put("required", field.isRequired());
        result.put("unique_value", field.isUniqueValue());
        result.put("searchable", field.isSearchable());
        result.put("shared", field.isShared());
        result.put("max_length", field.getMaxLength());
        result.put("precision_value", field.getPrecision());
        result.put("scale_value", field.getScale());
        result.put("reference_object_type_id", field.getReferenceObjectTypeId());
        result.put("default_value", field.getDefaultValue());
        result.put("validation_rule", field.getValidationRule());
        result.put("sort_order", field.getSortOrder());
        result.put("status", field.getStatus());
        return result;
    }

    private Map<String, Object> fieldResponse(FieldDefinition field) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", field.getId());
        result.put("field_key", field.getFieldKey());
        result.put("field_name", field.getFieldName());
        result.put("data_type", field.getDataType().name());
        result.put("required", field.isRequired());
        result.put("unique_value", field.isUniqueValue());
        result.put("searchable", field.isSearchable());
        result.put("shared", field.isShared());
        result.put("max_length", field.getMaxLength());
        result.put("precision_value", field.getPrecision());
        result.put("scale_value", field.getScale());
        result.put("reference_object_type_id", field.getReferenceObjectTypeId());
        result.put("default_value", field.getDefaultValue());
        result.put("validation_rule", field.getValidationRule());
        result.put("sort_order", field.getSortOrder());
        result.put("status", field.getStatus());
        return result;
    }
}
