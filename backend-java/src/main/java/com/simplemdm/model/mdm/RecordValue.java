package com.simplemdm.model.mdm;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_record_value")
public class RecordValue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "record_id", nullable = false) private Long recordId;
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

    protected RecordValue() { }
    private RecordValue(MdmRecord record, FieldDefinition field, TypedValue value, Long actorId) {
        systemId = record.getSystemId(); recordId = record.getId(); fieldDefinitionId = field.getId(); createdBy = actorId; updatedBy = actorId; apply(value, actorId);
    }
    public static RecordValue create(MdmRecord record, FieldDefinition field, TypedValue value, Long actorId) { return new RecordValue(record, field, value, actorId); }
    public void apply(TypedValue value, Long actorId) {
        stringValue = value.stringValue(); textValue = value.textValue(); integerValue = value.integerValue(); decimalValue = value.decimalValue();
        booleanValue = value.booleanValue(); dateValue = value.dateValue(); datetimeValue = value.datetimeValue(); referenceRecordId = value.referenceRecordId(); updatedBy = actorId;
    }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getRecordId() { return recordId; }
    public Long getFieldDefinitionId() { return fieldDefinitionId; }
    public int nonNullValueCount() { return typedValue().nonNullValueCount(); }
    public TypedValue typedValue() { return new TypedValue(stringValue, textValue, integerValue, decimalValue, booleanValue, dateValue, datetimeValue, referenceRecordId); }
}
