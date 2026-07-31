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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalServiceValidationTest {
    @Test
    void submitRejectsUnknownInvalidAndEmptyChangesBeforeCreatingRequest() {
        Fixture f=new Fixture(12L,7L);f.recordAndPermission();
        FieldDefinition enabled=f.booleanField();
        when(f.fields.findByObjectTypeId(8L)).thenReturn(List.of(enabled));
        assertThatThrownBy(()->f.submit(Map.of("unknown",true))).isInstanceOf(BusinessException.class).hasMessageContaining("Unknown");
        assertThatThrownBy(()->f.submit(Map.of("enabled","yes"))).isInstanceOf(BusinessException.class).hasMessageContaining("data type");
        assertThatThrownBy(()->f.submit(Map.of("enabled",true))).isInstanceOf(BusinessException.class).hasMessageContaining("at least one");
        verifyNoInteractions(f.requests);
    }

    @Test
    void referenceChangeUsesReferenceColumnsAndIsRehydratedForApprovedWriter() {
        Fixture f=new Fixture(20L,7L);MdmRecord record=f.recordAndPermission();
        ApprovalRequest request=ApprovalRequest.pending(7L,8L,41L,9L,12L,3L);ReflectionTestUtils.setField(request,"id",100L);
        FieldDefinition reference=f.referenceField();
        ApprovalChange change=ApprovalChange.create(7L,100L,56L,TypedValue.empty(),new TypedValue(null,null,null,null,null,null,null,77L));
        when(f.requests.findById(100L)).thenReturn(Optional.of(request));when(f.records.findBySystemIdAndId(7L,41L)).thenReturn(Optional.of(record));
        when(f.assignments.existsActiveAssignment(7L,8L,9L,20L)).thenReturn(true);when(f.authorization.can(20L,"APPROVAL_REVIEW",9L)).thenReturn(true);
        when(f.fields.findByObjectTypeId(8L)).thenReturn(List.of(reference));when(f.changes.findByApprovalRequestId(100L)).thenReturn(List.of(change));
        when(f.writer.apply(eq(20L),eq(41L),eq(3L),anyMap())).thenReturn(new RecordView(41L,7L,8L,9L,"EMP",4L));
        f.service.approve(100L,20L,3L);
        verify(f.writer).apply(20L,41L,3L,Map.of("manager",new TypedValueConverter.ReferenceValue(77L,10L,7L)));
    }

    private static class Fixture {
        final ApprovalRequestRepository requests=mock(ApprovalRequestRepository.class);final ApprovalChangeRepository changes=mock(ApprovalChangeRepository.class);final ApprovalActionRepository actions=mock(ApprovalActionRepository.class);
        final ApproverAssignmentRepository assignments=mock(ApproverAssignmentRepository.class);final MdmRecordRepository records=mock(MdmRecordRepository.class);final RecordValueRepository values=mock(RecordValueRepository.class);
        final FieldDefinitionRepository fields=mock(FieldDefinitionRepository.class);final AuthorizationService authorization=mock(AuthorizationService.class);final ApprovedRecordWriter writer=mock(ApprovedRecordWriter.class);
        final ApprovalService service;
        Fixture(Long userId,Long system){CurrentUserProvider current=mock(CurrentUserProvider.class);when(current.currentSystemUserId()).thenReturn(Optional.of(userId));UserRepository users=mock(UserRepository.class);User user=mock(User.class);when(user.isActive()).thenReturn(true);when(user.getSystemId()).thenReturn(system);when(users.findById(userId)).thenReturn(Optional.of(user));service=new ApprovalService(requests,changes,actions,assignments,records,values,fields,authorization,writer,new TypedValueConverter(),current,users);}
        MdmRecord recordAndPermission(){MdmRecord r=mock(MdmRecord.class);when(r.getId()).thenReturn(41L);when(r.getSystemId()).thenReturn(7L);when(r.getObjectTypeId()).thenReturn(8L);when(r.getDepartmentId()).thenReturn(9L);when(r.getVersion()).thenReturn(3L);when(records.findBySystemIdAndId(7L,41L)).thenReturn(Optional.of(r));when(authorization.can(anyLong(),eq("MDM_RECORD_EDIT"),eq(9L))).thenReturn(true);return r;}
        Long submit(Map<String,Object> data){return service.submit(new UpdateRecordCommand(7L,8L,41L,9L,3L,data),12L);}
        FieldDefinition booleanField(){FieldDefinition f=mock(FieldDefinition.class);when(f.getId()).thenReturn(55L);when(f.getFieldKey()).thenReturn("enabled");when(f.getDataType()).thenReturn(FieldDataType.BOOLEAN);RecordValue old=mock(RecordValue.class);when(old.getFieldDefinitionId()).thenReturn(55L);when(old.typedValue()).thenReturn(new TypedValue(null,null,null,null,true,null,null,null));when(values.findByRecordId(41L)).thenReturn(List.of(old));return f;}
        FieldDefinition referenceField(){FieldDefinition f=mock(FieldDefinition.class);when(f.getId()).thenReturn(56L);when(f.getFieldKey()).thenReturn("manager");when(f.getDataType()).thenReturn(FieldDataType.REFERENCE);when(f.getReferenceObjectTypeId()).thenReturn(10L);when(f.getSystemId()).thenReturn(7L);return f;}
    }
}
