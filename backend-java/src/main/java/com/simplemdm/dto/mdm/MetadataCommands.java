package com.simplemdm.dto.mdm;

import com.simplemdm.model.mdm.FieldDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MetadataCommands {
    private MetadataCommands() { }
    public record UpdateObjectType(
        @NotBlank @Size(max = 128) String name,
        boolean approval_required,
        boolean department_scoped) { }
    public record CreateField(
        @NotBlank @Size(max = 64) String field_key,
        @NotBlank @Size(max = 128) String field_name,
        @NotNull FieldDataType data_type,
        boolean required, boolean unique_value, boolean searchable,
        Integer max_length, Integer precision_value, Integer scale_value, Long reference_object_type_id,
        @Size(max = 2048) String default_value, @Size(max = 2048) String validation_rule, int sort_order) {
        public String fieldKey() { return field_key; } public String fieldName() { return field_name; }
        public FieldDataType dataType() { return data_type; } public boolean uniqueValue() { return unique_value; }
        public Integer maxLength() { return max_length; } public Integer precision() { return precision_value; }
        public Integer scale() { return scale_value; } public Long referenceObjectTypeId() { return reference_object_type_id; }
        public String defaultValue() { return default_value; } public String validationRule() { return validation_rule; }
        public int sortOrder() { return sort_order; }
    }
    public record UpdateField(
        @NotBlank @Size(max = 128) String field_name, @NotNull FieldDataType data_type,
        boolean required, boolean unique_value, boolean searchable,
        Integer max_length, Integer precision_value, Integer scale_value, Long reference_object_type_id,
        @Size(max = 2048) String default_value, @Size(max = 2048) String validation_rule, int sort_order) {
        public String fieldName() { return field_name; } public FieldDataType dataType() { return data_type; }
        public boolean uniqueValue() { return unique_value; } public Integer maxLength() { return max_length; }
        public Integer precision() { return precision_value; } public Integer scale() { return scale_value; }
        public Long referenceObjectTypeId() { return reference_object_type_id; }
        public String defaultValue() { return default_value; } public String validationRule() { return validation_rule; }
        public int sortOrder() { return sort_order; }
    }
    public record CreateChildType(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name,
                                  int sort_order) {
        public int sortOrder() { return sort_order; }
    }
    public record UpdateChildType(@NotBlank @Size(max = 128) String name, int sort_order) {
        public int sortOrder() { return sort_order; }
    }
    public record CreateChildField(
        @NotBlank @Size(max = 64) String field_key, @NotBlank @Size(max = 128) String field_name,
        @NotNull FieldDataType data_type, boolean required, boolean unique_value, boolean searchable, boolean shared,
        Integer max_length, Integer precision_value, Integer scale_value, Long reference_object_type_id,
        @Size(max = 2048) String default_value, @Size(max = 2048) String validation_rule, int sort_order) {
        public String fieldKey() { return field_key; } public String fieldName() { return field_name; }
        public FieldDataType dataType() { return data_type; } public boolean uniqueValue() { return unique_value; }
        public Integer maxLength() { return max_length; } public Integer precision() { return precision_value; }
        public Integer scale() { return scale_value; } public Long referenceObjectTypeId() { return reference_object_type_id; }
        public String defaultValue() { return default_value; } public String validationRule() { return validation_rule; }
        public int sortOrder() { return sort_order; }
    }
    public record UpdateChildField(
        @NotBlank @Size(max = 128) String field_name, @NotNull FieldDataType data_type,
        boolean required, boolean unique_value, boolean searchable, boolean shared,
        Integer max_length, Integer precision_value, Integer scale_value, Long reference_object_type_id,
        @Size(max = 2048) String default_value, @Size(max = 2048) String validation_rule, int sort_order) {
        public String fieldName() { return field_name; } public FieldDataType dataType() { return data_type; }
        public boolean uniqueValue() { return unique_value; } public Integer maxLength() { return max_length; }
        public Integer precision() { return precision_value; } public Integer scale() { return scale_value; }
        public Long referenceObjectTypeId() { return reference_object_type_id; }
        public String defaultValue() { return default_value; } public String validationRule() { return validation_rule; }
        public int sortOrder() { return sort_order; }
    }
}
