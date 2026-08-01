package com.simplemdm.service.workflow;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.ApprovalActionRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.repository.workflow.ApproverAssignmentRepository;
import com.simplemdm.service.mdm.CurrentUserProvider;
import com.simplemdm.service.mdm.TypedValueConverter;
import com.simplemdm.service.integration.PushEventService;
import com.simplemdm.service.system.AuthorizationService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalApplyExceptionMappingTest {
    @Mock private ApprovalRequestRepository requests;
    @Mock private ApprovalChangeRepository changes;
    @Mock private ApprovalChildChangeRepository childChanges;
    @Mock private ApprovalChildValueChangeRepository childValueChanges;
    @Mock private ApprovalActionRepository actions;
    @Mock private ApproverAssignmentRepository assignments;
    @Mock private ObjectTypeRepository objectTypes;
    @Mock private ChildTypeRepository childTypes;
    @Mock private FieldDefinitionRepository fields;
    @Mock private ChildFieldDefinitionRepository childFields;
    @Mock private MdmRecordRepository records;
    @Mock private RecordValueRepository values;
    @Mock private ChildRecordRepository childRecords;
    @Mock private ChildRecordValueRepository childValues;
    @Mock private DepartmentRepository departments;
    @Mock private AuthorizationService authorization;
    @Mock private CurrentUserProvider currentUser;
    @Mock private UserRepository users;
    @Mock private PushEventService pushEvents;

    private ApprovalApplyService service;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new ApprovalApplyService(requests, changes, childChanges, childValueChanges, actions,
            assignments, objectTypes, childTypes, fields, childFields, records, values, childRecords,
            childValues, departments, authorization, currentUser, users, new TypedValueConverter(), pushEvents);
        actor = mock(User.class);
        when(currentUser.currentSystemUserId()).thenReturn(Optional.of(7L));
        when(users.findById(7L)).thenReturn(Optional.of(actor));
        when(actor.isActive()).thenReturn(true);
        when(actor.isSystemActive()).thenReturn(true);
        when(actor.getSystemId()).thenReturn(10L);
    }

    @Test
    void mysqlLikePessimisticLockTimeoutMapsToConflict() {
        when(requests.findBySystemIdAndIdForUpdate(10L, 100L))
            .thenThrow(new PessimisticLockingFailureException(
                "Lock wait timeout exceeded; try restarting transaction"));

        assertThatThrownBy(() -> service.approve(100L, 7L))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void optimisticConcurrencyFailureIsNotMisclassifiedAsPessimisticLockConflict() {
        OptimisticLockingFailureException failure = new OptimisticLockingFailureException("stale entity");
        when(requests.findBySystemIdAndIdForUpdate(10L, 100L)).thenThrow(failure);

        assertThatThrownBy(() -> service.approve(100L, 7L)).isSameAs(failure);
    }

    @Test
    void mysqlRecordCodeConstraintMapsToConflictFromRootCauseMessage() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("could not execute insert",
            new SQLException("Duplicate entry 'EMP-1' for key 'uk_record_code'"));
        arrangeCreateUntilRecordInsert(failure);

        assertThatThrownBy(() -> service.approve(100L, 7L))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void h2RecordCodeConstraintMapsToConflictFromExtractedConstraintName() {
        ConstraintViolationException hibernateFailure = new ConstraintViolationException("could not execute",
            new SQLException("unique violation"), "insert into mdm_record", "UK_RECORD_CODE");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("insert failed",
            hibernateFailure);
        arrangeCreateUntilRecordInsert(failure);

        assertThatThrownBy(() -> service.approve(100L, 7L))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void unrelatedIntegrityConstraintIsRethrownUnchanged() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("could not execute insert",
            new SQLException("Integrity constraint violation: fk_record_department_system"));
        arrangeCreateUntilRecordInsert(failure);

        assertThatThrownBy(() -> service.approve(100L, 7L)).isSameAs(failure);
    }

    private void arrangeCreateUntilRecordInsert(DataIntegrityViolationException failure) {
        ApprovalRequest request = ApprovalRequest.pending(10L, 20L, ApprovalRequest.Operation.CREATE,
            null, "EMP-1", 30L, 7L, null);
        ReflectionTestUtils.setField(request, "id", 100L);
        ObjectType objectType = mock(ObjectType.class);
        when(objectType.isActive()).thenReturn(true);
        Department department = mock(Department.class);
        when(requests.findBySystemIdAndIdForUpdate(10L, 100L)).thenReturn(Optional.of(request));
        when(objectTypes.findBySystemIdAndIdForUpdate(10L, 20L)).thenReturn(Optional.of(objectType));
        when(departments.findActiveBySystemIdAndId(10L, 30L)).thenReturn(Optional.of(department));
        when(department.getId()).thenReturn(30L);
        when(assignments.existsActiveAssignment(10L, 20L, 30L, 7L)).thenReturn(true);
        when(authorization.canInStrictSelfScope(7L, "APPROVAL_REVIEW", 30L)).thenReturn(true);
        when(childChanges.findByApprovalRequestIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());
        when(fields.findByObjectTypeId(20L)).thenReturn(List.of());
        when(changes.findByApprovalRequestId(100L)).thenReturn(List.of());
        when(records.findByRecordCodeForUpdate(10L, 20L, "EMP-1")).thenReturn(List.of());
        when(records.saveAndFlush(any(MdmRecord.class))).thenThrow(failure);
    }
}
