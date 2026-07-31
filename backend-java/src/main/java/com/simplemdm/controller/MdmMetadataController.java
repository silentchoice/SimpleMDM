package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mdm")
public class MdmMetadataController {
    private final ObjectTypeRepository objectTypes;
    private final FieldDefinitionRepository fields;

    public MdmMetadataController(ObjectTypeRepository objectTypes, FieldDefinitionRepository fields) {
        this.objectTypes = objectTypes;
        this.fields = fields;
    }

    @GetMapping("/object-types")
    public ApiResponse objectTypes() {
        Long systemId = SystemController.currentUser().getSystemId();
        var types = objectTypes.findBySystemId(systemId);
        Map<Long, List<FieldDefinition>> definitions = types.isEmpty()
            ? Map.of()
            : fields.findBySystemIdAndObjectTypeIdIn(systemId, types.stream().map(type -> type.getId()).toList())
                .stream().filter(field -> "active".equals(field.getStatus()))
                .collect(Collectors.groupingBy(FieldDefinition::getObjectTypeId));
        return ApiResponse.ok(types.stream().map(type -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", type.getId());
            item.put("code", type.getCode());
            item.put("name", type.getName());
            item.put("fields", definitions.getOrDefault(type.getId(), List.of()).stream()
                .sorted(java.util.Comparator.comparingInt(FieldDefinition::getSortOrder))
                .map(this::fieldResponse).toList());
            return item;
        }).toList());
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
        return result;
    }
}
