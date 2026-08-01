package com.simplemdm.model.workflow;

import com.simplemdm.model.mdm.TypedValue;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name="wf_approval_change")
public class ApprovalChange {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="system_id",nullable=false) private Long systemId;
    @Column(name="approval_request_id",nullable=false) private Long approvalRequestId;
    @Column(name="field_definition_id",nullable=false) private Long fieldDefinitionId;
    @Column(name="old_string_value",length=4096) private String oldStringValue; @Column(name="old_text_value") private String oldTextValue;
    @Column(name="old_integer_value") private Long oldIntegerValue; @Column(name="old_decimal_value",precision=38,scale=10) private BigDecimal oldDecimalValue;
    @Column(name="old_boolean_value") private Boolean oldBooleanValue; @Column(name="old_date_value") private LocalDate oldDateValue;
    @Column(name="old_datetime_value") private LocalDateTime oldDatetimeValue; @Column(name="old_reference_record_id") private Long oldReferenceRecordId;
    @Column(name="new_string_value",length=4096) private String newStringValue; @Column(name="new_text_value") private String newTextValue;
    @Column(name="new_integer_value") private Long newIntegerValue; @Column(name="new_decimal_value",precision=38,scale=10) private BigDecimal newDecimalValue;
    @Column(name="new_boolean_value") private Boolean newBooleanValue; @Column(name="new_date_value") private LocalDate newDateValue;
    @Column(name="new_datetime_value") private LocalDateTime newDatetimeValue; @Column(name="new_reference_record_id") private Long newReferenceRecordId;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    protected ApprovalChange(){}
    public static ApprovalChange create(Long systemId,Long requestId,Long fieldId,TypedValue oldValue,TypedValue newValue){
        ApprovalChange c=new ApprovalChange(); c.systemId=systemId;c.approvalRequestId=requestId;c.fieldDefinitionId=fieldId;c.setOld(oldValue);c.setNew(newValue);return c;
    }
    private void setOld(TypedValue v){oldStringValue=v.stringValue();oldTextValue=v.textValue();oldIntegerValue=v.integerValue();oldDecimalValue=v.decimalValue();oldBooleanValue=v.booleanValue();oldDateValue=v.dateValue();oldDatetimeValue=v.datetimeValue();oldReferenceRecordId=v.referenceRecordId();}
    private void setNew(TypedValue v){newStringValue=v.stringValue();newTextValue=v.textValue();newIntegerValue=v.integerValue();newDecimalValue=v.decimalValue();newBooleanValue=v.booleanValue();newDateValue=v.dateValue();newDatetimeValue=v.datetimeValue();newReferenceRecordId=v.referenceRecordId();}
    @PrePersist void create(){createdAt=LocalDateTime.now();}
    public Long getId(){return id;} public Long getSystemId(){return systemId;}
    public Long getApprovalRequestId(){return approvalRequestId;} public Long getFieldDefinitionId(){return fieldDefinitionId;}
    public TypedValue oldValue(){return new TypedValue(oldStringValue,oldTextValue,oldIntegerValue,oldDecimalValue,oldBooleanValue,oldDateValue,oldDatetimeValue,oldReferenceRecordId);}
    public TypedValue newValue(){return new TypedValue(newStringValue,newTextValue,newIntegerValue,newDecimalValue,newBooleanValue,newDateValue,newDatetimeValue,newReferenceRecordId);}
}
