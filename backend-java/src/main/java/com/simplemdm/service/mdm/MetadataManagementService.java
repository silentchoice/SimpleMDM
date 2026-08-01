package com.simplemdm.service.mdm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.dto.mdm.MetadataCommands;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.*;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.*;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class MetadataManagementService {
    private static final String FIELD_MANAGE = "MDM_FIELD_MANAGE";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectTypeRepository objectTypes; private final FieldDefinitionRepository fields;
    private final RecordValueRepository recordValues; private final MetadataAuditRepository audits;
    private final ChildTypeRepository childTypes; private final ChildFieldDefinitionRepository childFields;
    private final ChildRecordValueRepository childValues;
    private final AuthorizationService authorization; private final MetadataService metadataValidation;
    public MetadataManagementService(ObjectTypeRepository objectTypes, FieldDefinitionRepository fields,
                                     RecordValueRepository recordValues, MetadataAuditRepository audits,
                                     AuthorizationService authorization, MetadataService metadataValidation) {
        this(objectTypes, fields, recordValues, null, null, null, audits, authorization, metadataValidation);
    }
    @Autowired
    public MetadataManagementService(ObjectTypeRepository objectTypes, FieldDefinitionRepository fields,
                                     RecordValueRepository recordValues, ChildTypeRepository childTypes,
                                     ChildFieldDefinitionRepository childFields, ChildRecordValueRepository childValues,
                                     MetadataAuditRepository audits, AuthorizationService authorization, MetadataService metadataValidation) {
        this.objectTypes = objectTypes; this.fields = fields; this.recordValues = recordValues;
        this.childTypes = childTypes; this.childFields = childFields; this.childValues = childValues;
        this.audits = audits; this.authorization = authorization; this.metadataValidation = metadataValidation;
    }
    public record FieldMutation(FieldDefinition field, Long auditId) { }
    public record ObjectTypeMutation(ObjectType objectType, Long auditId) { }
    public record ChildTypeMutation(ChildType childType, Long auditId) { }
    public record ChildFieldMutation(ChildFieldDefinition field, Long auditId) { }

    public FieldMutation createMasterField(User user, String objectCode, MetadataCommands.CreateField command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode);
        if (fields.existsByObjectTypeIdAndFieldKey(objectType.getId(), command.fieldKey()))
            throw new BusinessException(409, "Field key already exists");
        CreateFieldCommand configuration = createCommand(command);
        FieldDefinition field = FieldDefinition.create(objectType.getId(), objectType, configuration,
            metadataValidation.validateAndResolveReference(objectType, configuration));
        field.markCreatedBy(user.getId());
        field = fields.save(field);
        return mutation(user, field, "CREATE", null, snapshot(field));
    }

    public FieldMutation updateMasterField(User user, String objectCode, Long fieldId, MetadataCommands.UpdateField command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); FieldDefinition field = field(user, fieldId);
        if (!objectType.getId().equals(field.getObjectTypeId())) throw new BusinessException(404, "Field not found");
        String before = snapshot(field);
        if (!recordValues.findActiveByFieldDefinitionId(fieldId).isEmpty()
            && changesValueSemantics(field, command))
            throw new BusinessException(409, "Used field value constraints cannot change");
        metadataValidation.validateAndResolveReference(objectType, createCommand(field, command));
        field.apply(command, user.getId());
        return mutation(user, fields.save(field), "UPDATE", before, snapshot(field));
    }

    public FieldMutation deactivateMasterField(User user, String objectCode, Long fieldId) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); FieldDefinition field = field(user, fieldId);
        if (!objectType.getId().equals(field.getObjectTypeId())) throw new BusinessException(404, "Field not found");
        String before = snapshot(field); field.deactivate(user.getId());
        return mutation(user, fields.save(field), "DEACTIVATE", before, snapshot(field));
    }

    public FieldMutation reactivateMasterField(User user, String objectCode, Long fieldId) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); FieldDefinition field = fieldIncludingInactive(user, fieldId);
        if (!objectType.getId().equals(field.getObjectTypeId())) throw new BusinessException(404, "Field not found");
        String before = snapshot(field); field.reactivate(user.getId());
        return mutation(user, fields.save(field), "REACTIVATE", before, snapshot(field));
    }

    public ObjectTypeMutation updateObjectType(User user, String objectCode, MetadataCommands.UpdateObjectType command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode);
        String before = snapshot(objectType);
        objectType.apply(command.name(), command.approval_required(), command.department_scoped(), user.getId());
        return objectTypeMutation(user, objectTypes.save(objectType), "UPDATE", before, snapshot(objectType));
    }

    public ObjectTypeMutation deactivateObjectType(User user, String objectCode) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode);
        String before = snapshot(objectType); objectType.deactivate(user.getId());
        return objectTypeMutation(user, objectTypes.save(objectType), "DEACTIVATE", before, snapshot(objectType));
    }

    public ObjectTypeMutation reactivateObjectType(User user, String objectCode) {
        requireManager(user); ObjectType objectType = objectTypeIncludingInactive(user, objectCode);
        String before = snapshot(objectType); objectType.reactivate(user.getId());
        return objectTypeMutation(user, objectTypes.save(objectType), "REACTIVATE", before, snapshot(objectType));
    }

    public ChildTypeMutation createChildType(User user, String objectCode, MetadataCommands.CreateChildType command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode);
        if (childTypes.findBySystemIdAndObjectTypeIdAndCode(user.getSystemId(), objectType.getId(), command.code()).isPresent())
            throw new BusinessException(409, "Child type code already exists");
        ChildType childType = ChildType.create(objectType.getId(), objectType, command.code(), command.name());
        childType.apply(new MetadataCommands.UpdateChildType(command.name(), command.sortOrder()), user.getId());
        childType.markCreatedBy(user.getId()); childType = childTypes.save(childType);
        return childTypeMutation(user, childType, "CREATE", null, snapshot(childType));
    }

    public ChildTypeMutation updateChildType(User user, String objectCode, Long childTypeId, MetadataCommands.UpdateChildType command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childType(user, childTypeId);
        if (!objectType.getId().equals(childType.getObjectTypeId())) throw new BusinessException(404, "Child type not found");
        String before = snapshot(childType); childType.apply(command, user.getId());
        return childTypeMutation(user, childTypes.save(childType), "UPDATE", before, snapshot(childType));
    }

    public ChildTypeMutation deactivateChildType(User user, String objectCode, Long childTypeId) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childType(user, childTypeId);
        if (!objectType.getId().equals(childType.getObjectTypeId())) throw new BusinessException(404, "Child type not found");
        String before = snapshot(childType); childType.deactivate(user.getId());
        return childTypeMutation(user, childTypes.save(childType), "DEACTIVATE", before, snapshot(childType));
    }

    public ChildTypeMutation reactivateChildType(User user, String objectCode, Long childTypeId) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childTypeIncludingInactive(user, childTypeId);
        requireChildTypeOf(objectType, childType); String before = snapshot(childType); childType.reactivate(user.getId());
        return childTypeMutation(user, childTypes.save(childType), "REACTIVATE", before, snapshot(childType));
    }

    public ChildFieldMutation createChildField(User user, String objectCode, Long childTypeId, MetadataCommands.CreateChildField command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childType(user, childTypeId);
        requireChildTypeOf(objectType, childType);
        if (childFields.existsByChildTypeIdAndFieldKey(childTypeId, command.fieldKey())) throw new BusinessException(409, "Child field key already exists");
        CreateFieldCommand configuration = createCommand(command);
        ChildFieldDefinition field = ChildFieldDefinition.create(childTypeId, childType, configuration,
            metadataValidation.validateAndResolveReference(objectType, configuration));
        field.markCreatedBy(user.getId()); field = childFields.save(field);
        return childFieldMutation(user, field, "CREATE", null, snapshot(field));
    }

    public ChildFieldMutation updateChildField(User user, String objectCode, Long childTypeId, Long fieldId,
                                                MetadataCommands.UpdateChildField command) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childType(user, childTypeId);
        requireChildTypeOf(objectType, childType); ChildFieldDefinition field = childField(user, fieldId);
        if (!childTypeId.equals(field.getChildTypeId())) throw new BusinessException(404, "Child field not found");
        String before = snapshot(field);
        if (!childValues.findActiveByFieldDefinitionId(fieldId).isEmpty()
            && changesValueSemantics(field, command))
            throw new BusinessException(409, "Used field value constraints cannot change");
        metadataValidation.validateAndResolveReference(objectType, createCommand(field, command));
        field.apply(command, user.getId());
        return childFieldMutation(user, childFields.save(field), "UPDATE", before, snapshot(field));
    }

    public ChildFieldMutation deactivateChildField(User user, String objectCode, Long childTypeId, Long fieldId) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childType(user, childTypeId);
        requireChildTypeOf(objectType, childType); ChildFieldDefinition field = childField(user, fieldId);
        if (!childTypeId.equals(field.getChildTypeId())) throw new BusinessException(404, "Child field not found");
        String before = snapshot(field); field.deactivate(user.getId());
        return childFieldMutation(user, childFields.save(field), "DEACTIVATE", before, snapshot(field));
    }

    public ChildFieldMutation reactivateChildField(User user, String objectCode, Long childTypeId, Long fieldId) {
        requireManager(user); ObjectType objectType = objectType(user, objectCode); ChildType childType = childType(user, childTypeId);
        requireChildTypeOf(objectType, childType); ChildFieldDefinition field = childFieldIncludingInactive(user, fieldId);
        if (!childTypeId.equals(field.getChildTypeId())) throw new BusinessException(404, "Child field not found");
        String before = snapshot(field); field.reactivate(user.getId());
        return childFieldMutation(user, childFields.save(field), "REACTIVATE", before, snapshot(field));
    }

    private FieldMutation mutation(User user, FieldDefinition field, String action, String before, String after) {
        MetadataAudit audit = audits.save(MetadataAudit.create(user.getSystemId(), user.getId(), "MASTER_FIELD",
            field.getId(), action, before, after));
        return new FieldMutation(field, audit.getId());
    }
    private ObjectTypeMutation objectTypeMutation(User user, ObjectType objectType, String action, String before, String after) {
        MetadataAudit audit = audits.save(MetadataAudit.create(user.getSystemId(), user.getId(), "OBJECT_TYPE",
            objectType.getId(), action, before, after));
        return new ObjectTypeMutation(objectType, audit.getId());
    }
    private ChildTypeMutation childTypeMutation(User user, ChildType childType, String action, String before, String after) {
        MetadataAudit audit = audits.save(MetadataAudit.create(user.getSystemId(), user.getId(), "CHILD_TYPE",
            childType.getId(), action, before, after));
        return new ChildTypeMutation(childType, audit.getId());
    }
    private ChildFieldMutation childFieldMutation(User user, ChildFieldDefinition field, String action, String before, String after) {
        MetadataAudit audit = audits.save(MetadataAudit.create(user.getSystemId(), user.getId(), "CHILD_FIELD",
            field.getId(), action, before, after));
        return new ChildFieldMutation(field, audit.getId());
    }
    private ObjectType objectType(User user, String code) { return objectTypes.findBySystemIdAndCode(user.getSystemId(), code)
        .filter(ObjectType::isActive)
        .orElseThrow(() -> new BusinessException(404, "Object type not found")); }
    private ObjectType objectTypeIncludingInactive(User user, String code) { return objectTypes.findBySystemIdAndCode(user.getSystemId(), code)
        .orElseThrow(() -> new BusinessException(404, "Object type not found")); }
    private FieldDefinition field(User user, Long id) { return fields.findBySystemIdAndId(user.getSystemId(), id)
        .filter(value -> "active".equals(value.getStatus()))
        .orElseThrow(() -> new BusinessException(404, "Field not found")); }
    private FieldDefinition fieldIncludingInactive(User user, Long id) { return fields.findBySystemIdAndId(user.getSystemId(), id)
        .orElseThrow(() -> new BusinessException(404, "Field not found")); }
    private ChildType childType(User user, Long id) { return childTypes.findBySystemIdAndId(user.getSystemId(), id)
        .filter(value -> "active".equals(value.getStatus()))
        .orElseThrow(() -> new BusinessException(404, "Child type not found")); }
    private ChildType childTypeIncludingInactive(User user, Long id) { return childTypes.findBySystemIdAndId(user.getSystemId(), id)
        .orElseThrow(() -> new BusinessException(404, "Child type not found")); }
    private ChildFieldDefinition childField(User user, Long id) { return childFields.findBySystemIdAndId(user.getSystemId(), id)
        .filter(value -> "active".equals(value.getStatus()))
        .orElseThrow(() -> new BusinessException(404, "Child field not found")); }
    private ChildFieldDefinition childFieldIncludingInactive(User user, Long id) { return childFields.findBySystemIdAndId(user.getSystemId(), id)
        .orElseThrow(() -> new BusinessException(404, "Child field not found")); }
    private void requireChildTypeOf(ObjectType objectType, ChildType childType) {
        if (!objectType.getId().equals(childType.getObjectTypeId())) throw new BusinessException(404, "Child type not found");
    }
    private void requireManager(User user) {
        if (user == null || user.getId() == null || user.getSystemId() == null) throw new BusinessException(401, "System user required");
        if (!user.isSystemAdmin() && !authorization.can(user.getId(), FIELD_MANAGE, user.getDepartmentId()))
            throw new BusinessException(403, "Field management permission required");
    }
    private CreateFieldCommand createCommand(MetadataCommands.CreateField command) {
        return new CreateFieldCommand(command.fieldKey(), command.fieldName(), command.dataType(), command.required(),
            command.uniqueValue(), command.searchable(), false, command.maxLength(), command.precision_value(),
            command.scale_value(), command.referenceObjectTypeId(), command.defaultValue(), command.validationRule(), command.sortOrder());
    }
    private CreateFieldCommand createCommand(FieldDefinition field, MetadataCommands.UpdateField command) {
        return new CreateFieldCommand(field.getFieldKey(), command.fieldName(), command.dataType(), command.required(),
            command.uniqueValue(), command.searchable(), false, command.maxLength(), command.precision(), command.scale(),
            command.referenceObjectTypeId(), command.defaultValue(), command.validationRule(), command.sortOrder());
    }
    private CreateFieldCommand createCommand(MetadataCommands.CreateChildField command) {
        return new CreateFieldCommand(command.fieldKey(), command.fieldName(), command.dataType(), command.required(),
            command.uniqueValue(), command.searchable(), command.shared(), command.maxLength(), command.precision(),
            command.scale(), command.referenceObjectTypeId(), command.defaultValue(), command.validationRule(), command.sortOrder());
    }
    private CreateFieldCommand createCommand(ChildFieldDefinition field, MetadataCommands.UpdateChildField command) {
        return new CreateFieldCommand(field.getFieldKey(), command.fieldName(), command.dataType(), command.required(),
            command.uniqueValue(), command.searchable(), command.shared(), command.maxLength(), command.precision(),
            command.scale(), command.referenceObjectTypeId(), command.defaultValue(), command.validationRule(), command.sortOrder());
    }
    private boolean changesValueSemantics(FieldDefinition field, MetadataCommands.UpdateField command) {
        return field.getDataType() != command.dataType() || field.isRequired() != command.required()
            || field.isUniqueValue() != command.uniqueValue()
            || !Objects.equals(field.getMaxLength(), command.maxLength())
            || !Objects.equals(field.getPrecision(), command.precision())
            || !Objects.equals(field.getScale(), command.scale())
            || !Objects.equals(field.getReferenceObjectTypeId(), command.referenceObjectTypeId())
            || !Objects.equals(field.getDefaultValue(), command.defaultValue())
            || !Objects.equals(field.getValidationRule(), command.validationRule());
    }
    private boolean changesValueSemantics(ChildFieldDefinition field, MetadataCommands.UpdateChildField command) {
        return field.getDataType() != command.dataType() || field.isRequired() != command.required()
            || field.isUniqueValue() != command.uniqueValue()
            || !Objects.equals(field.getMaxLength(), command.maxLength())
            || !Objects.equals(field.getPrecision(), command.precision())
            || !Objects.equals(field.getScale(), command.scale())
            || !Objects.equals(field.getReferenceObjectTypeId(), command.referenceObjectTypeId())
            || !Objects.equals(field.getDefaultValue(), command.defaultValue())
            || !Objects.equals(field.getValidationRule(), command.validationRule());
    }
    private String snapshot(FieldDefinition field) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("field_key", field.getFieldKey()); values.put("field_name", field.getFieldName());
        values.put("data_type", field.getDataType()); values.put("required", field.isRequired());
        values.put("unique_value", field.isUniqueValue()); values.put("searchable", field.isSearchable());
        values.put("max_length", field.getMaxLength()); values.put("precision_value", field.getPrecision());
        values.put("scale_value", field.getScale()); values.put("reference_object_type_id", field.getReferenceObjectTypeId());
        values.put("default_value", field.getDefaultValue()); values.put("validation_rule", field.getValidationRule());
        values.put("sort_order", field.getSortOrder()); values.put("status", field.getStatus());
        try { return JSON.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize metadata audit snapshot", exception); }
    }
    private String snapshot(ObjectType objectType) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("code", objectType.getCode());
        values.put("name", objectType.getName()); values.put("approval_required", objectType.isApprovalRequired());
        values.put("department_scoped", objectType.isDepartmentScoped()); values.put("status", objectType.getStatus());
        return json(values);
    }
    private String snapshot(ChildType childType) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("code", childType.getCode());
        values.put("name", childType.getName()); values.put("sort_order", childType.getSortOrder()); values.put("status", childType.getStatus());
        return json(values);
    }
    private String snapshot(ChildFieldDefinition field) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("field_key", field.getFieldKey());
        values.put("field_name", field.getFieldName()); values.put("data_type", field.getDataType()); values.put("required", field.isRequired());
        values.put("unique_value", field.isUniqueValue()); values.put("searchable", field.isSearchable()); values.put("shared", field.isShared());
        values.put("max_length", field.getMaxLength()); values.put("precision_value", field.getPrecision()); values.put("scale_value", field.getScale());
        values.put("reference_object_type_id", field.getReferenceObjectTypeId()); values.put("default_value", field.getDefaultValue());
        values.put("validation_rule", field.getValidationRule()); values.put("sort_order", field.getSortOrder()); values.put("status", field.getStatus());
        return json(values);
    }
    private String json(Map<String, Object> values) {
        try { return JSON.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize metadata audit snapshot", exception); }
    }
}
