package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RecordApprovalServiceTest {
  private final RecordApprovalRepository approvals = Mockito.mock(RecordApprovalRepository.class);
  private final RecordRepository records = Mockito.mock(RecordRepository.class);
  private final EditLockStore locks = Mockito.mock(EditLockStore.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final RecordSnapshotCodec snapshots = new RecordSnapshotCodec(new ObjectMapper());
  private final RecordApprovalService service =
      new RecordApprovalService(approvals, records, locks, authorization, snapshots);

  @AfterEach void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test void submitAtomicallyBindsOneTaskAndFreezesTheCompleteDraft() {
    RecordDraft draft = draft(91, RecordAction.CREATE, RecordStatus.DRAFT, null, 0, 12);
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
    when(approvals.lockDraft(7, 91)).thenReturn(new RecordApprovalRepository.BoundDraft(draft, null));
    when(approvals.submit(7, 12, 91, null, snapshots.encode(draft))).thenReturn(701L);

    assertThat(service.submit(91, null)).isEqualTo(701L);

    verify(authorization).requireDepartment(7);
    verify(approvals).submit(7, 12, 91, null, snapshots.encode(draft));
    verify(approvals).markPending(7, 91, 701);
  }

  @Test void updateSubmitValidatesTheHeldTokenAndReleasesOnlyAfterCommit() {
    RecordDraft draft = draft(91, RecordAction.UPDATE, RecordStatus.DRAFT, 81L, 3, 12);
    RecordView formal = formal(81, 3, "ACTIVE");
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
    when(approvals.lockDraft(7, 91)).thenReturn(new RecordApprovalRepository.BoundDraft(draft, formal));
    when(locks.find(81)).thenReturn(new EditLock(81, 7, 12, "Editor", "held-token",
        Instant.parse("2026-08-06T09:30:00Z")));
    when(approvals.submit(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
        Mockito.anyString(), Mockito.anyString())).thenReturn(701L);
    TransactionSynchronizationManager.initSynchronization();

    service.submit(91, "held-token");

    verify(locks, never()).release(81, "held-token");
    List<TransactionSynchronization> callbacks =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(callbacks).hasSize(1);
    callbacks.get(0).afterCommit();
    verify(locks).release(81, "held-token");
  }

  @Test void committedSubmissionIsNotReportedFailedWhenAdvisoryLockCleanupIsUnavailable() {
    RecordDraft draft = draft(91, RecordAction.UPDATE, RecordStatus.DRAFT, 81L, 3, 12);
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
    when(approvals.lockDraft(7, 91)).thenReturn(
        new RecordApprovalRepository.BoundDraft(draft, formal(81, 3, "ACTIVE")));
    when(locks.find(81)).thenReturn(new EditLock(81, 7, 12, "Editor", "held-token",
        Instant.parse("2026-08-06T09:30:00Z")));
    when(approvals.submit(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
        Mockito.anyString(), Mockito.anyString())).thenReturn(701L);
    when(locks.release(81, "held-token")).thenThrow(new IllegalStateException("redis down"));
    TransactionSynchronizationManager.initSynchronization();
    service.submit(91, "held-token");

    assertThat(TransactionSynchronizationManager.getSynchronizations()).singleElement()
        .satisfies(callback -> org.assertj.core.api.Assertions.assertThatCode(callback::afterCommit)
            .doesNotThrowAnyException());
  }

  @Test void updateSubmitRejectsAStaleDatabaseVersionBeforeCreatingATask() {
    RecordDraft draft = draft(91, RecordAction.UPDATE, RecordStatus.DRAFT, 81L, 3, 12);
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor());
    when(approvals.lockDraft(7, 91)).thenReturn(
        new RecordApprovalRepository.BoundDraft(draft, formal(81, 4, "ACTIVE")));

    assertThatThrownBy(() -> service.submit(91, "held-token"))
        .isInstanceOfSatisfying(BusinessException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.getMessage()).isEqualTo("Record version changed");
        });
    verify(approvals, never()).submit(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
        Mockito.any(), Mockito.anyString());
  }

  @Test void approverCannotApproveTheirOwnSubmission() {
    RecordDraft draft = draft(91, RecordAction.CREATE, RecordStatus.PENDING, null, 0, 23);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
    when(approvals.lockTask(7, 701)).thenReturn(task(701, 91, 23, draft, "PENDING"));

    assertThatThrownBy(() -> service.approve(701, "looks good"))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(records, never()).activate(Mockito.anyLong(), Mockito.anyLong());
  }

  @Test void approveVerifiesTaskDraftAndSnapshotBindingBeforeActivation() {
    RecordDraft draft = draft(92, RecordAction.CREATE, RecordStatus.PENDING, null, 0, 12);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
    when(approvals.lockTask(7, 701)).thenReturn(task(701, 91, 12, draft, "PENDING"));
    when(approvals.lockDraft(7, 91)).thenReturn(new RecordApprovalRepository.BoundDraft(draft, null));

    assertThatThrownBy(() -> service.approve(701, null))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getMessage()).isEqualTo("Invalid record snapshot"));
    verify(records, never()).activate(Mockito.anyLong(), Mockito.anyLong());
  }

  @Test void approveActivatesCreateUpdateAndDeleteAndCompletesTheTask() {
    for (RecordAction action : RecordAction.values()) {
      Long recordId = action == RecordAction.CREATE ? null : 81L;
      long base = action == RecordAction.CREATE ? 0 : 3;
      RecordDraft draft = draft(91, action, RecordStatus.PENDING, recordId, base, 12);
      when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
      when(approvals.lockTask(7, 701)).thenReturn(task(701, 91, 12, draft, "PENDING"));
      when(approvals.lockDraft(7, 91)).thenReturn(new RecordApprovalRepository.BoundDraft(draft,
          recordId == null ? null : formal(81, 3, "ACTIVE")));
      when(records.activate(91, 23)).thenReturn(formal(recordId == null ? 99 : 81,
          action == RecordAction.CREATE ? 1 : 4, action == RecordAction.DELETE ? "DELETED" : "ACTIVE"));

      RecordView activated = service.approve(701, " looks good ");

      assertThat(activated.version()).isEqualTo(action == RecordAction.CREATE ? 1 : 4);
      verify(approvals).approve(7, 701, 23, "looks good");
      Mockito.clearInvocations(approvals, records, authorization);
    }
  }

  @Test void activationConflictLeavesThePendingTaskUnchanged() {
    RecordDraft draft = draft(91, RecordAction.UPDATE, RecordStatus.PENDING, 81L, 3, 12);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
    when(approvals.lockTask(7, 701)).thenReturn(task(701, 91, 12, draft, "PENDING"));
    when(approvals.lockDraft(7, 91)).thenReturn(
        new RecordApprovalRepository.BoundDraft(draft, formal(81, 4, "ACTIVE")));

    assertThatThrownBy(() -> service.approve(701, null))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getMessage()).isEqualTo("Record version changed"));
    verify(approvals, never()).approve(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
        Mockito.any());
  }

  @Test void rejectionChangesOnlyTheTaskAndDraftAndDuplicateActionsAreConflicts() {
    RecordDraft draft = draft(91, RecordAction.UPDATE, RecordStatus.PENDING, 81L, 3, 12);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
    when(approvals.lockTask(7, 701)).thenReturn(task(701, 91, 12, draft, "PENDING"));
    when(approvals.lockDraft(7, 91)).thenReturn(
        new RecordApprovalRepository.BoundDraft(draft, formal(81, 3, "ACTIVE")));

    service.reject(701, " needs correction ");

    verify(approvals).reject(7, 701, 91, 23, "needs correction");
    verify(records, never()).activate(Mockito.anyLong(), Mockito.anyLong());

    when(approvals.lockTask(7, 701)).thenReturn(task(701, 91, 12, draft, "REJECTED"));
    assertThatThrownBy(() -> service.reject(701, "again"))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getMessage()).isEqualTo("Approval task is not pending"));
  }

  @Test void malformedReviewInputsAreRejectedBeforeStateChanges() {
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver());
    assertThatThrownBy(() -> service.reject(701, "  "))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getMessage()).isEqualTo("Rejection reason is required"));
    assertThatThrownBy(() -> service.approve(701, "x".repeat(1001)))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getMessage()).isEqualTo(
                "Approval comment must not exceed 1000 characters"));
  }

  private RecordApprovalRepository.ApprovalTask task(long id, long draftId, long submitter,
      RecordDraft draft, String status) {
    String before = draft.action() == RecordAction.CREATE ? null
        : snapshots.encode(new RecordDraft(draft.id(), draft.recordId(), draft.masterTypeId(),
            draft.departmentId(), draft.recordCode(), draft.action(), draft.baseVersion(),
            draft.masterValues(), draft.children(), RecordStatus.APPROVED, draft.createdBy(),
            draft.deleteReason()));
    return new RecordApprovalRepository.ApprovalTask(id, 7, draftId, before,
        snapshots.encode(draft), status, submitter);
  }

  private RecordDraft draft(long id, RecordAction action, RecordStatus status, Long recordId,
      long baseVersion, long creator) {
    return new RecordDraft(id, recordId, 9, 7, "CUS-1", action, baseVersion,
        Map.of("name", "North"), List.of(new RecordDraft.ChildRows(31,
            List.of(new RecordDraft.ChildRow(null, 0, Map.of("contact", "Li"))))),
        status, creator, action == RecordAction.DELETE ? "duplicate" : null);
  }

  private RecordView formal(long id, long version, String status) {
    return new RecordView(id, 9, 7, "CUS-1", Map.of("name", "North"), List.of(), version, status);
  }

  private UserPrincipal editor() {
    return new UserPrincipal(12, "editor", "Editor", department(), List.of(Role.DEPT_EDITOR));
  }

  private UserPrincipal approver() {
    return new UserPrincipal(23, "approver", "Approver", department(), List.of(Role.DEPT_APPROVER));
  }

  private DepartmentPrincipal department() {
    return new DepartmentPrincipal(7, "SALES", "Sales");
  }
}
