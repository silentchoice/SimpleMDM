package com.simplemdm.model.mdm;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_child_record_value")
public class ChildRecordValue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "child_record_id", nullable = false) private Long childRecordId;
    @Column(name = "field_definition_id", nullable = false) private Long fieldDefinitionId;
    @Column(name = "string_value", length = 4096) private String stringValue;
    @Column(name = "text_value", columnDefinition = "TEXT") private String textValue;
    @Column(name = "integer_value") private Long integerValue;
    @Column(name = "decimal_value", precision = 38, scale = 10) private BigDecimal decimalValue;
    @Column(name = "boolean_value") private Boolean booleanValue;
    @Column(name = "date_value") private LocalDate dateValue;
    @Column(name = "datetime_value") private LocalDateTime datetimeValue;
    @Column(name = "reference_record_id") private Long referenceRecordId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Version @Column(nullable = false) private Long version;
    protected ChildRecordValue() { }
    private ChildRecordValue(ChildRecord child, ChildFieldDefinition field, TypedValue value, Long actorId) {
        systemId = child.getSystemId(); childRecordId = child.getId(); fieldDefinitionId = field.getId(); createdBy = actorId; updatedBy = actorId;
        stringValue = value.stringValue(); textValue = value.textValue(); integerValue = value.integerValue(); decimalValue = value.decimalValue();
        booleanValue = value.booleanValue(); dateValue = value.dateValue(); datetimeValue = value.datetimeValue(); referenceRecordId = value.referenceRecordId();
    }
    public static ChildRecordValue create(ChildRecord child, ChildFieldDefinition field, TypedValue value, Long actorId) {
        return new ChildRecordValue(child, field, value, actorId);
    }
    public Long getChildRecordId() { return childRecordId; }
    public TypedValue typedValue() { return new TypedValue(stringValue, textValue, integerValue, decimalValue, booleanValue, dateValue, datetimeValue, referenceRecordId); }
    public int nonNullValueCount() {
        return new TypedValue(stringValue, textValue, integerValue, decimalValue, booleanValue, dateValue, datetimeValue, referenceRecordId).nonNullValueCount();
    }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
