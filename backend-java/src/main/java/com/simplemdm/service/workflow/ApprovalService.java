package com.simplemdm.service.workflow;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.*;
import com.simplemdm.model.workflow.*;
import com.simplemdm.repository.mdm.*;
import com.simplemdm.repository.workflow.*;
import com.simplemdm.service.mdm.*;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("relationalApprovalService")
public class ApprovalService {
    private final ApprovalRequestRepository requests; private final ApprovalChangeRepository changes;
    private final ApprovalActionRepository actions; private final ApproverAssignmentRepository assignments;
    private final MdmRecordRepository records; private final RecordValueRepository values;
    private final FieldDefinitionRepository fields; private final AuthorizationService authorization;
    private final RecordService recordService;
    public ApprovalService(ApprovalRequestRepository requests,ApprovalChangeRepository changes,ApprovalActionRepository actions,
      ApproverAssignmentRepository assignments,MdmRecordRepository records,RecordValueRepository values,
      FieldDefinitionRepository fields,AuthorizationService authorization,RecordService recordService){
        this.requests=requests;this.changes=changes;this.actions=actions;this.assignments=assignments;this.records=records;
        this.values=values;this.fields=fields;this.authorization=authorization;this.recordService=recordService;
    }

    @Transactional
    public Long submit(UpdateRecordCommand command, Long applicantId) {
        MdmRecord record=records.findBySystemIdAndId(command.systemId(),command.recordId())
            .orElseThrow(()->new BusinessException(404,"Record not found"));
        requireRoute(command,record);
        if(record.getVersion()!=command.expectedVersion()) throw new BusinessException(409,"Record version conflict");
        ApprovalRequest request=requests.save(ApprovalRequest.pending(command.systemId(),command.objectTypeId(),
            command.recordId(),command.departmentId(),applicantId,command.expectedVersion()));
        Map<Long,RecordValue> old=values.findByRecordId(record.getId()).stream()
            .collect(Collectors.toMap(RecordValue::getFieldDefinitionId,Function.identity()));
        List<ApprovalChange> rows=new ArrayList<>();
        for(FieldDefinition field:fields.findByObjectTypeId(command.objectTypeId())){
            if(!command.data().containsKey(field.getFieldKey())) continue;
            TypedValue before=Optional.ofNullable(old.get(field.getId())).map(RecordValue::typedValue).orElse(TypedValue.empty());
            TypedValue after=typed(field,command.data().get(field.getFieldKey()));
            if(!before.equals(after)) rows.add(ApprovalChange.create(command.systemId(),request.getId(),field.getId(),before,after));
        }
        changes.saveAll(rows);
        return request.getId();
    }

    @Transactional
    public RecordView approve(Long requestId,Long approverId,long expectedRecordVersion){
        ApprovalRequest request=requests.findById(requestId).orElseThrow(()->new BusinessException(404,"Approval request not found"));
        if(!"PENDING".equals(request.getStatus())) throw new BusinessException(409,"Approval request is not pending");
        MdmRecord record=records.findBySystemIdAndId(request.getSystemId(),request.getRecordId())
            .orElseThrow(()->new BusinessException(404,"Record not found"));
        if(!record.getObjectTypeId().equals(request.getObjectTypeId())||!record.getDepartmentId().equals(request.getDepartmentId()))
            throw new BusinessException(409,"Approval target no longer matches request");
        if(record.getVersion()!=request.getExpectedVersion()||record.getVersion()!=expectedRecordVersion)
            throw new BusinessException(409,"Record version conflict; resubmit approval");
        if(!assignments.existsActiveAssignment(request.getSystemId(),request.getObjectTypeId(),request.getDepartmentId(),approverId))
            throw new BusinessException(403,"Approver is not assigned to this target");
        if(!authorization.can(approverId,"APPROVAL_REVIEW",request.getDepartmentId()))
            throw new BusinessException(403,"Approver lacks permission");
        Map<Long,FieldDefinition> definitions=fields.findByObjectTypeId(request.getObjectTypeId()).stream()
            .collect(Collectors.toMap(FieldDefinition::getId,Function.identity()));
        Map<String,Object> data=new LinkedHashMap<>();
        for(ApprovalChange change:changes.findByApprovalRequestId(requestId)){
            FieldDefinition definition=definitions.get(change.getFieldDefinitionId());
            if(definition==null) throw new BusinessException(409,"Approval field no longer exists");
            data.put(definition.getFieldKey(),untyped(change.newValue()));
        }
        RecordView result=recordService.update(record.getId(),expectedRecordVersion,data);
        request.approve(); requests.save(request); actions.save(ApprovalAction.approved(request.getSystemId(),requestId,approverId));
        return result;
    }
    private void requireRoute(UpdateRecordCommand c,MdmRecord r){
        if(!r.getObjectTypeId().equals(c.objectTypeId())||!r.getDepartmentId().equals(c.departmentId()))
            throw new BusinessException(400,"Approval target does not match record");
    }
    private TypedValue typed(FieldDefinition f,Object raw){
        if(raw==null)return TypedValue.empty();
        try{return switch(f.getDataType()){
            case STRING->new TypedValue(raw.toString(),null,null,null,null,null,null,null);
            case TEXT->new TypedValue(null,raw.toString(),null,null,null,null,null,null);
            case INTEGER->new TypedValue(null,null,raw instanceof Number n?n.longValue():Long.valueOf(raw.toString()),null,null,null,null,null);
            case DECIMAL->new TypedValue(null,null,null,raw instanceof BigDecimal b?b:new BigDecimal(raw.toString()),null,null,null,null);
            case BOOLEAN->new TypedValue(null,null,null,null,raw instanceof Boolean b?b:Boolean.valueOf(raw.toString()),null,null,null);
            case DATE->new TypedValue(null,null,null,null,null,raw instanceof LocalDate d?d:LocalDate.parse(raw.toString()),null,null);
            case DATETIME->new TypedValue(null,null,null,null,null,null,raw instanceof LocalDateTime d?d:LocalDateTime.parse(raw.toString()),null);
            case REFERENCE->new TypedValue(null,null,null,null,null,null,null,raw instanceof Number n?n.longValue():Long.valueOf(raw.toString()));
        };}catch(RuntimeException e){throw new BusinessException(400,"Invalid value for field "+f.getFieldKey());}
    }
    private Object untyped(TypedValue v){if(v.stringValue()!=null)return v.stringValue();if(v.textValue()!=null)return v.textValue();if(v.integerValue()!=null)return v.integerValue();if(v.decimalValue()!=null)return v.decimalValue();if(v.booleanValue()!=null)return v.booleanValue();if(v.dateValue()!=null)return v.dateValue();if(v.datetimeValue()!=null)return v.datetimeValue();return v.referenceRecordId();}
}
