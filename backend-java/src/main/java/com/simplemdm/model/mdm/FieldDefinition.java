package com.simplemdm.model.mdm;

import com.simplemdm.service.mdm.CreateFieldCommand;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_field_definition", uniqueConstraints = {
    @UniqueConstraint(name = "uk_field_definition_key", columnNames = {"object_type_id", "field_key"}),
    @UniqueConstraint(name = "uk_field_system_id", columnNames = {"system_id", "id"})
})
public class FieldDefinition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "object_type_id", nullable = false) private Long objectTypeId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
        @JoinColumn(name = "system_id", referencedColumnName = "system_id", insertable = false, updatable = false),
        @JoinColumn(name = "object_type_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_field_object_system"))
    private ObjectType objectType;
    @Column(name = "field_key", nullable = false, length = 64) private String fieldKey;
    @Column(name = "field_name", nullable = false, length = 128) private String fieldName;
    @Enumerated(EnumType.STRING) @Column(name = "data_type", nullable = false, length = 16) private FieldDataType dataType;
    @Column(nullable = false) private boolean required;
    @Column(name = "unique_value", nullable = false) private boolean uniqueValue;
    @Column(nullable = false) private boolean searchable;
    @Column(nullable = false) private boolean shared;
    @Column(name = "max_length") private Integer maxLength;
    @Column(name = "precision_value") private Integer precision;
    @Column(name = "scale_value") private Integer scale;
    @Column(name = "reference_object_type_id") private Long referenceObjectTypeId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
        @JoinColumn(name = "system_id", referencedColumnName = "system_id", insertable = false, updatable = false),
        @JoinColumn(name = "reference_object_type_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_field_reference_system"))
    private ObjectType referenceObjectType;
    @Column(name = "default_value", length = 2048) private String defaultValue;
    @Column(name = "validation_rule", length = 2048) private String validationRule;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Version @Column(nullable = false) private Long version;

    protected FieldDefinition() { }

    private FieldDefinition(Long objectTypeId, ObjectType objectType, CreateFieldCommand command, ObjectType referenceObjectType) {
        this.systemId = objectType.getSystemId();
        this.objectTypeId = objectTypeId;
        this.objectType = objectType;
        this.fieldKey = command.fieldKey();
        this.fieldName = command.fieldName();
        this.dataType = command.dataType();
        this.required = command.required();
        this.uniqueValue = command.uniqueValue();
        this.searchable = command.searchable();
        this.shared = command.shared();
        this.maxLength = command.maxLength();
        this.precision = command.precision();
        this.scale = command.scale();
        this.referenceObjectTypeId = command.referenceObjectTypeId();
        this.referenceObjectType = referenceObjectType;
        this.defaultValue = command.defaultValue();
        this.validationRule = command.validationRule();
        this.sortOrder = command.sortOrder();
        this.status = "active";
    }

    public static FieldDefinition create(Long objectTypeId, ObjectType objectType, CreateFieldCommand command,
                                         ObjectType referenceObjectType) {
        return new FieldDefinition(objectTypeId, objectType, command, referenceObjectType);
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getObjectTypeId() { return objectTypeId; }
    public String getFieldKey() { return fieldKey; }
    public String getFieldName() { return fieldName; }
    public FieldDataType getDataType() { return dataType; }
    public boolean isRequired() { return required; }
    public boolean isUniqueValue() { return uniqueValue; }
    public boolean isSearchable() { return searchable; }
    public boolean isShared() { return shared; }
    public Integer getMaxLength() { return maxLength; }
    public Integer getPrecision() { return precision; }
    public Integer getScale() { return scale; }
    public Long getReferenceObjectTypeId() { return referenceObjectTypeId; }
    public String getDefaultValue() { return defaultValue; }
    public String getValidationRule() { return validationRule; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }
}
