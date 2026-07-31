package com.simplemdm.service.workflow;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.*;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.*;
import com.simplemdm.repository.mdm.*;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.*;
import com.simplemdm.service.mdm.*;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("relationalApprovalService")
public class ApprovalService {
 private final ApprovalRequestRepository requests; private final ApprovalChangeRepository changes; private final ApprovalActionRepository actions; private final ApproverAssignmentRepository assignments;
 private final MdmRecordRepository records; private final RecordValueRepository values; private final FieldDefinitionRepository fields; private final AuthorizationService authorization;
 private final ApprovedRecordWriter writer; private final TypedValueConverter converter; private final CurrentUserProvider currentUser; private final UserRepository users;
 public ApprovalService(ApprovalRequestRepository requests,ApprovalChangeRepository changes,ApprovalActionRepository actions,ApproverAssignmentRepository assignments,MdmRecordRepository records,RecordValueRepository values,FieldDefinitionRepository fields,AuthorizationService authorization,ApprovedRecordWriter writer,TypedValueConverter converter,CurrentUserProvider currentUser,UserRepository users){this.requests=requests;this.changes=changes;this.actions=actions;this.assignments=assignments;this.records=records;this.values=values;this.fields=fields;this.authorization=authorization;this.writer=writer;this.converter=converter;this.currentUser=currentUser;this.users=users;}
 @Transactional public Long submit(UpdateRecordCommand command,Long applicantId){if(command==null)throw new BusinessException(400,"Update command is required");
  Long actor=authenticatedAs(applicantId); User user=activeUser(actor); MdmRecord record=records.findBySystemIdAndId(command.systemId(),command.recordId()).orElseThrow(()->new BusinessException(404,"Record not found"));
  if(!user.getSystemId().equals(command.systemId()))throw new BusinessException(403,"User belongs to another system"); requireRoute(command,record);
  if(!authorization.can(actor,"MDM_RECORD_EDIT",record.getDepartmentId()))throw new BusinessException(403,"Applicant cannot edit this record"); if(record.getVersion()!=command.expectedVersion())throw new BusinessException(409,"Record version conflict");
  List<FieldDefinition> definitions=fields.findByObjectTypeId(command.objectTypeId()); Map<String,FieldDefinition> byKey=definitions.stream().collect(Collectors.toMap(FieldDefinition::getFieldKey,Function.identity()));
  if(command.data()==null||!byKey.keySet().containsAll(command.data().keySet()))throw new BusinessException(400,"Unknown field key"); Map<Long,RecordValue> old=values.findByRecordId(record.getId()).stream().collect(Collectors.toMap(RecordValue::getFieldDefinitionId,Function.identity())); List<PendingChange> pending=new ArrayList<>();
  for(var entry:command.data().entrySet()){FieldDefinition field=byKey.get(entry.getKey());TypedValue before=Optional.ofNullable(old.get(field.getId())).map(RecordValue::typedValue).orElse(TypedValue.empty());TypedValue after=converter.convert(field,entry.getValue());validateReference(field,entry.getValue());if(!before.equals(after))pending.add(new PendingChange(field.getId(),before,after));}
  if(pending.isEmpty())throw new BusinessException(400,"Approval requires at least one changed field"); ApprovalRequest request=requests.save(ApprovalRequest.pending(command.systemId(),command.objectTypeId(),command.recordId(),command.departmentId(),actor,command.expectedVersion())); changes.saveAll(pending.stream().map(p->ApprovalChange.create(command.systemId(),request.getId(),p.fieldId(),p.before(),p.after())).toList()); actions.save(ApprovalAction.submitted(command.systemId(),request.getId(),actor)); return request.getId();
 }
 @Transactional public RecordView approve(Long requestId,Long approverId,long expectedRecordVersion){
  Long actor=authenticatedAs(approverId);User user=activeUser(actor);ApprovalRequest request=requests.findById(requestId).orElseThrow(()->new BusinessException(404,"Approval request not found"));if(!user.getSystemId().equals(request.getSystemId()))throw new BusinessException(403,"Approver belongs to another system");if(!"PENDING".equals(request.getStatus()))throw new BusinessException(409,"Approval request is not pending");MdmRecord record=records.findBySystemIdAndId(request.getSystemId(),request.getRecordId()).orElseThrow(()->new BusinessException(404,"Record not found"));
  if(!record.getObjectTypeId().equals(request.getObjectTypeId())||!record.getDepartmentId().equals(request.getDepartmentId()))throw new BusinessException(409,"Approval target no longer matches request");if(record.getVersion()!=request.getExpectedVersion()||record.getVersion()!=expectedRecordVersion)throw new BusinessException(409,"Record version conflict; resubmit approval");if(!assignments.existsActiveAssignment(request.getSystemId(),request.getObjectTypeId(),request.getDepartmentId(),actor))throw new BusinessException(403,"Approver is not assigned to this target");if(!authorization.can(actor,"APPROVAL_REVIEW",request.getDepartmentId()))throw new BusinessException(403,"Approver lacks permission");
  Map<Long,FieldDefinition> definitions=fields.findByObjectTypeId(request.getObjectTypeId()).stream().collect(Collectors.toMap(FieldDefinition::getId,Function.identity()));Map<String,Object> data=new LinkedHashMap<>();for(ApprovalChange change:changes.findByApprovalRequestId(requestId)){FieldDefinition definition=definitions.get(change.getFieldDefinitionId());if(definition==null)throw new BusinessException(409,"Approval field no longer exists");data.put(definition.getFieldKey(),apiValue(definition,change.newValue()));}if(data.isEmpty())throw new BusinessException(409,"Approval contains no changes");RecordView result=writer.apply(request,actor,expectedRecordVersion,data);request.approve();requests.saveAndFlush(request);actions.save(ApprovalAction.approved(request.getSystemId(),requestId,actor));return result;
 }
 private Long authenticatedAs(Long claimed){Long actual=currentUser.currentSystemUserId().orElseThrow(()->new BusinessException(401,"No authenticated system user"));if(claimed==null||!actual.equals(claimed))throw new BusinessException(403,"Claimed user is not the authenticated user");return actual;}
 private User activeUser(Long id){User user=users.findById(id).orElseThrow(()->new BusinessException(401,"Authenticated user not found"));if(!user.isActive())throw new BusinessException(403,"Authenticated user is inactive");return user;}
 private void requireRoute(UpdateRecordCommand c,MdmRecord r){if(!r.getObjectTypeId().equals(c.objectTypeId())||!r.getDepartmentId().equals(c.departmentId()))throw new BusinessException(400,"Approval target does not match record");}
 private void validateReference(FieldDefinition field,Object raw){if(field.getDataType()!=FieldDataType.REFERENCE||raw==null)return;TypedValueConverter.ReferenceValue ref=(TypedValueConverter.ReferenceValue)raw;MdmRecord target=records.findById(ref.recordId()).orElseThrow(()->new BusinessException(404,"Referenced record not found"));if(!field.getSystemId().equals(target.getSystemId())||!field.getReferenceObjectTypeId().equals(target.getObjectTypeId()))throw new BusinessException(400,"Referenced record must match the field system and object type");}
 private Object apiValue(FieldDefinition f,TypedValue v){if(f.getDataType()==FieldDataType.REFERENCE)return v.referenceRecordId()==null?null:new TypedValueConverter.ReferenceValue(v.referenceRecordId(),f.getReferenceObjectTypeId(),f.getSystemId());if(v.stringValue()!=null)return v.stringValue();if(v.textValue()!=null)return v.textValue();if(v.integerValue()!=null)return v.integerValue();if(v.decimalValue()!=null)return v.decimalValue();if(v.booleanValue()!=null)return v.booleanValue();if(v.dateValue()!=null)return v.dateValue();return v.datetimeValue();}
 private record PendingChange(Long fieldId,TypedValue before,TypedValue after){}
}