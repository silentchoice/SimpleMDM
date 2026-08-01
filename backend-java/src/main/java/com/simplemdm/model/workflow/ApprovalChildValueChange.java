package com.simplemdm.model.workflow;

import com.simplemdm.model.mdm.TypedValue;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "wf_approval_child_value_change", uniqueConstraints =
    @UniqueConstraint(name = "uk_approval_child_value_field", columnNames = {"approval_child_change_id", "field_definition_id"}))
public class ApprovalChildValueChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "approval_child_change_id", nullable = false) private Long approvalChildChangeId;
    @Column(name = "field_definition_id", nullable = false) private Long fieldDefinitionId;
    @Column(name = "old_string_value", length = 4096) private String oldStringValue;
    @Column(name = "old_text_value", columnDefinition = "TEXT") private String oldTextValue;
    @Column(name = "old_integer_value") private Long oldIntegerValue;
    @Column(name = "old_decimal_value", precision = 38, scale = 10) private BigDecimal oldDecimalValue;
    @Column(name = "old_boolean_value") private Boolean oldBooleanValue;
    @Column(name = "old_date_value") private LocalDate oldDateValue;
    @Column(name = "old_datetime_value") private LocalDateTime oldDatetimeValue;
    @Column(name = "old_reference_record_id") private Long oldReferenceRecordId;
    @Column(name = "new_string_value", length = 4096) private String newStringValue;
    @Column(name = "new_text_value", columnDefinition = "TEXT") private String newTextValue;
    @Column(name = "new_integer_value") private Long newIntegerValue;
    @Column(name = "new_decimal_value", precision = 38, scale = 10) private BigDecimal newDecimalValue;
    @Column(name = "new_boolean_value") private Boolean newBooleanValue;
    @Column(name = "new_date_value") private LocalDate newDateValue;
    @Column(name = "new_datetime_value") private LocalDateTime newDatetimeValue;
    @Column(name = "new_reference_record_id") private Long newReferenceRecordId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected ApprovalChildValueChange() { }

    public static ApprovalChildValueChange create(Long systemId, Long approvalChildChangeId, Long fieldDefinitionId,
                                                  TypedValue oldValue, TypedValue newValue) {
        ApprovalChildValueChange change = new ApprovalChildValueChange();
        change.systemId = systemId;
        change.approvalChildChangeId = approvalChildChangeId;
        change.fieldDefinitionId = fieldDefinitionId;
        change.setOld(oldValue == null ? TypedValue.empty() : oldValue);
        change.setNew(newValue == null ? TypedValue.empty() : newValue);
        return change;
    }

    private void setOld(TypedValue value) {
        oldStringValue = value.stringValue(); oldTextValue = value.textValue(); oldIntegerValue = value.integerValue();
        oldDecimalValue = value.decimalValue(); oldBooleanValue = value.booleanValue(); oldDateValue = value.dateValue();
        oldDatetimeValue = value.datetimeValue(); oldReferenceRecordId = value.referenceRecordId();
    }

    private void setNew(TypedValue value) {
        newStringValue = value.stringValue(); newTextValue = value.textValue(); newIntegerValue = value.integerValue();
        newDecimalValue = value.decimalValue(); newBooleanValue = value.booleanValue(); newDateValue = value.dateValue();
        newDatetimeValue = value.datetimeValue(); newReferenceRecordId = value.referenceRecordId();
    }

    @PrePersist
    void create() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getApprovalChildChangeId() { return approvalChildChangeId; }
    public Long getFieldDefinitionId() { return fieldDefinitionId; }
    public TypedValue oldValue() { return new TypedValue(oldStringValue, oldTextValue, oldIntegerValue, oldDecimalValue, oldBooleanValue, oldDateValue, oldDatetimeValue, oldReferenceRecordId); }
    public TypedValue newValue() { return new TypedValue(newStringValue, newTextValue, newIntegerValue, newDecimalValue, newBooleanValue, newDateValue, newDatetimeValue, newReferenceRecordId); }
}
