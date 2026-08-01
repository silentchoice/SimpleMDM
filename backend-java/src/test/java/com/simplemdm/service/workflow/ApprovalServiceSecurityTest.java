package com.simplemdm.service.workflow;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.*;
import com.simplemdm.service.mdm.*;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApprovalServiceSecurityTest {
    @Test
    void submitRejectsSpoofedApplicantBeforeWriting() {
        Fixture f = new Fixture(12L);
        assertThatThrownBy(() -> f.service.submit(
            new UpdateRecordCommand(7L, 8L, 41L, 9L, 3L, Map.of()), 99L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("authenticated");
        verifyNoInteractions(f.requests);
    }

    @Test
    void approveRejectsSpoofedApproverBeforeWriting() {
        Fixture f = new Fixture(20L);
        assertThatThrownBy(() -> f.service.approve(100L, 99L, 3L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("authenticated");
        verifyNoInteractions(f.requests);
    }

    @Test
    void nullCommandIsBadRequest() {
        Fixture f = new Fixture(12L);
        assertThatThrownBy(() -> f.service.submit((UpdateRecordCommand) null, 12L)).isInstanceOf(BusinessException.class);
    }

    private static class Fixture {
        final ApprovalRequestRepository requests=mock(ApprovalRequestRepository.class);
        final ApprovalService service;
        Fixture(Long currentId) {
            CurrentUserProvider current=mock(CurrentUserProvider.class);
            when(current.currentSystemUserId()).thenReturn(Optional.of(currentId));
            service=new ApprovalService(requests,mock(ApprovalChangeRepository.class),
                mock(ApprovalActionRepository.class),mock(ApproverAssignmentRepository.class),
                mock(MdmRecordRepository.class),mock(RecordValueRepository.class),
                mock(FieldDefinitionRepository.class),mock(AuthorizationService.class),
                mock(ApprovedRecordWriter.class),mock(TypedValueConverter.class),
                current,mock(UserRepository.class));
        }
    }
}
