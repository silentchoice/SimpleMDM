package com.simplemdm.service.workflow;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.workflow.ApprovalActionRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.repository.workflow.ApproverAssignmentRepository;
import com.simplemdm.service.mdm.ApprovedRecordWriter;
import com.simplemdm.service.mdm.CurrentUserProvider;
import com.simplemdm.service.mdm.TypedValueConverter;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.mdm.UpdateRecordCommand;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {
    private final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    private final ApprovalChangeRepository changes = mock(ApprovalChangeRepository.class);
    private final ApprovalActionRepository actions = mock(ApprovalActionRepository.class);
    private final ApproverAssignmentRepository assignments = mock(ApproverAssignmentRepository.class);
    private final MdmRecordRepository records = mock(MdmRecordRepository.class);
    private final RecordValueRepository values = mock(RecordValueRepository.class);
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ApprovedRecordWriter writer = mock(ApprovedRecordWriter.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TypedValueConverter converter = new TypedValueConverter();
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(requests, changes, actions, assignments, records, values, fields,
            authorization, writer, converter, currentUser, users);
    }

    @Test
    void submitStoresOneRelationalTypedChangePerChangedField() {
        authenticate(12L, 7L);
        when(authorization.can(12L, "MDM_RECORD_EDIT", 9L)).thenReturn(true);
        MdmRecord record = record(41L, 7L, 8L, 9L, 3L);
        FieldDefinition salary = field(55L, "salary");
        RecordValue oldSalary = mock(RecordValue.class);
        when(oldSalary.getFieldDefinitionId()).thenReturn(55L);
        when(oldSalary.typedValue()).thenReturn(new TypedValue(null, null, null,
            new BigDecimal("100.00"), null, null, null, null));
        when(records.findBySystemIdAndId(7L, 41L)).thenReturn(Optional.of(record));
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(salary));
        when(values.findByRecordId(41L)).thenReturn(List.of(oldSalary));
        AtomicLong ids = new AtomicLong(100);
        when(requests.save(any())).thenAnswer(invocation -> {
            ApprovalRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", ids.getAndIncrement());
            return request;
        });
        when(changes.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Long requestId = service.submit(new UpdateRecordCommand(
            7L, 8L, 41L, 9L, 3L, Map.of("salary", new BigDecimal("125.50"))), 12L);

        ArgumentCaptor<List<ApprovalChange>> captor = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(captor.capture());
        assertThat(requestId).isEqualTo(100L);
        assertThat(captor.getValue()).singleElement().satisfies(change -> {
            assertThat(change.getFieldDefinitionId()).isEqualTo(55L);
            assertThat(change.oldValue().decimalValue()).isEqualByComparingTo("100.00");
            assertThat(change.newValue().decimalValue()).isEqualByComparingTo("125.50");
        });
    }

    @Test
    void approveRejectsStaleRecordVersionBeforeApplyingChanges() {
        authenticate(20L, 7L);
        ApprovalRequest request = ApprovalRequest.pending(7L, 8L, 41L, 9L, 12L, 3L);
        ReflectionTestUtils.setField(request, "id", 100L);
        MdmRecord record = record(41L, 7L, 8L, 9L, 4L);
        when(requests.findById(100L)).thenReturn(Optional.of(request));
        when(records.findBySystemIdAndId(7L, 41L)).thenReturn(Optional.of(record));
        when(writer.apply(100L)).thenThrow(new BusinessException(409, "Record version conflict"));

        assertThatThrownBy(() -> service.approve(100L, 20L, 3L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("version");
        verify(writer).apply(100L);
    }

    @Test
    void approveRequiresActiveAssignmentPermissionAndMatchingSystemBeforeUpdate() {
        authenticate(20L, 7L);
        ApprovalRequest request = ApprovalRequest.pending(7L, 8L, 41L, 9L, 12L, 3L);
        ReflectionTestUtils.setField(request, "id", 100L);
        MdmRecord record = record(41L, 7L, 8L, 9L, 3L);
        when(requests.findById(100L)).thenReturn(Optional.of(request));
        when(records.findBySystemIdAndId(7L, 41L)).thenReturn(Optional.of(record));
        when(assignments.existsActiveAssignment(7L, 8L, 9L, 20L)).thenReturn(true);
        when(authorization.can(20L, "APPROVAL_REVIEW", 9L)).thenReturn(true);
        FieldDefinition salary = field(55L, "salary");
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(salary));
        when(changes.findByApprovalRequestId(100L)).thenReturn(List.of(ApprovalChange.create(7L, 100L, 55L, TypedValue.empty(), new TypedValue(null, null, null, new BigDecimal("125.50"), null, null, null, null))));
        RecordView expected = new RecordView(41L, 7L, 8L, 9L, "EMP-41", 4L);
        when(writer.apply(100L)).thenReturn(expected);

        RecordView actual = service.approve(100L, 20L, 3L);

        assertThat(actual).isEqualTo(expected);
        verify(writer).apply(100L);
    }

    private void authenticate(Long id, Long systemId) {
        when(currentUser.currentSystemUserId()).thenReturn(Optional.of(id));
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.getSystemId()).thenReturn(systemId);
        when(users.findById(id)).thenReturn(Optional.of(user));
    }
    private static MdmRecord record(Long id, Long system, Long type, Long department, Long version) {
        MdmRecord record = mock(MdmRecord.class);
        when(record.getId()).thenReturn(id);
        when(record.getSystemId()).thenReturn(system);
        when(record.getObjectTypeId()).thenReturn(type);
        when(record.getDepartmentId()).thenReturn(department);
        when(record.getVersion()).thenReturn(version);
        return record;
    }

    private static FieldDefinition field(Long id, String key) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getId()).thenReturn(id);
        when(field.getFieldKey()).thenReturn(key);
        when(field.getDataType()).thenReturn(com.simplemdm.model.mdm.FieldDataType.DECIMAL);
        when(field.getPrecision()).thenReturn(10);
        when(field.getScale()).thenReturn(2);
        return field;
    }
}


