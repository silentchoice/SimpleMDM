package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataService {

    private final ObjectTypeRepository objectTypes;
    private final FieldDefinitionRepository fields;

    public MetadataService(ObjectTypeRepository objectTypes, FieldDefinitionRepository fields) {
        this.objectTypes = objectTypes;
        this.fields = fields;
    }

    @Transactional
    public FieldDefinition createField(Long objectTypeId, CreateFieldCommand command) {
        ObjectType objectType = objectTypes.findById(objectTypeId)
            .orElseThrow(() -> new BusinessException(404, "Object type not found"));
        validate(command);
        if (fields.existsByObjectTypeIdAndFieldKey(objectTypeId, command.fieldKey())) {
            throw new BusinessException(409, "Duplicate field key for object type");
        }
        ObjectType referenceObjectType = referenceObjectType(objectType, command);
        return fields.save(FieldDefinition.create(objectTypeId, objectType, command, referenceObjectType));
    }

    private ObjectType referenceObjectType(ObjectType objectType, CreateFieldCommand command) {
        if (command.dataType() != FieldDataType.REFERENCE) return null;
        ObjectType reference = objectTypes.findById(command.referenceObjectTypeId())
            .orElseThrow(() -> new BusinessException(404, "Reference object type not found"));
        if (!objectType.getSystemId().equals(reference.getSystemId())) {
            throw new BusinessException(400, "Reference object type must belong to the same system");
        }
        return reference;
    }

    private void validate(CreateFieldCommand command) {
        if (command == null || isBlank(command.fieldKey()) || isBlank(command.fieldName()) || command.dataType() == null) {
            throw new BusinessException(400, "Field key, name, and data type are required");
        }
        if (command.dataType() == FieldDataType.REFERENCE && command.referenceObjectTypeId() == null) {
            throw new BusinessException(400, "Reference fields require a reference object type");
        }
        if (command.dataType() != FieldDataType.REFERENCE && command.referenceObjectTypeId() != null) {
            throw new BusinessException(400, "Only reference fields may define a reference object type");
        }
        if (command.maxLength() != null && command.maxLength() <= 0) throw new BusinessException(400, "Max length must be positive");
        if (command.maxLength() != null && command.dataType() != FieldDataType.STRING && command.dataType() != FieldDataType.TEXT) {
            throw new BusinessException(400, "Max length applies only to string or text fields");
        }
        if ((command.precision() != null || command.scale() != null) && command.dataType() != FieldDataType.DECIMAL) {
            throw new BusinessException(400, "Precision and scale apply only to decimal fields");
        }
        if (command.precision() != null && command.precision() <= 0) throw new BusinessException(400, "Precision must be positive");
        if (command.scale() != null && command.scale() < 0) throw new BusinessException(400, "Scale must not be negative");
        if (command.precision() != null && command.scale() != null && command.scale() > command.precision()) {
            throw new BusinessException(400, "Scale must not exceed precision");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
