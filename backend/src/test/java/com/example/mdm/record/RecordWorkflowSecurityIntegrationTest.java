package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mdm.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RecordWorkflowSecurityIntegrationTest {
  @Test void secondEditorCannotAcquireAnotherEditorsRecordLock() throws Exception {
    try (WorkflowHarness workflow = WorkflowHarness.start()) {
      RecordView formal = workflow.createAndApprove("Locked");
      EditLock held = workflow.acquireAs(workflow.editor(), formal.id());

      assertThatThrownBy(() -> workflow.acquireAs(workflow.secondEditor(), formal.id()))
          .isInstanceOfSatisfying(EditLockConflictException.class, conflict -> {
            assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.details().holderId()).isEqualTo(workflow.editor().id());
            assertThat(conflict.details().holderDisplayName()).isEqualTo("Editor One");
            assertThat(!conflict.getMessage().contains(held.token())).isTrue();
          });
      assertStoredLockMatches(workflow.storedLock(formal.id()), held);
    }
  }

  @Test void submitRejectsAStaleBaseVersionAndPreservesTheDraftAndLock() throws Exception {
    try (WorkflowHarness workflow = WorkflowHarness.start()) {
      RecordView formal = workflow.createAndApprove("Version 1");
      RecordDraft stale = workflow.updateDraft(formal, "Stale version");
      EditLock held = workflow.acquireAs(workflow.editor(), formal.id());
      workflow.advanceFormalVersion(formal.id());

      assertThatThrownBy(() -> workflow.submit(stale.id(), held.token()))
          .isInstanceOfSatisfying(BusinessException.class, conflict -> {
            assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.getMessage()).isEqualTo("Record version changed");
          });
      assertThat(workflow.storedDraft(stale.id()).status()).isEqualTo(RecordStatus.DRAFT);
      assertStoredLockMatches(workflow.storedLock(formal.id()), held);
    }
  }

  @Test void submitterCannotApproveTheirOwnCreateRequest() throws Exception {
    try (WorkflowHarness workflow = WorkflowHarness.start()) {
      RecordDraft draft = workflow.createRecordDraft("Self approval", "private", java.util.List.of());
      long taskId = workflow.submitAs(workflow.editor(), draft.id(), null);

      assertThatThrownBy(() -> workflow.approveAs(workflow.selfApprover(), taskId))
          .isInstanceOfSatisfying(BusinessException.class,
              denied -> assertThat(denied.status()).isEqualTo(HttpStatus.FORBIDDEN));
      assertThat(workflow.storedDraft(draft.id()).status()).isEqualTo(RecordStatus.PENDING);
    }
  }

  private void assertStoredLockMatches(EditLock actual, EditLock expected) {
    assertThat(actual).extracting(EditLock::recordId, EditLock::departmentId, EditLock::userId,
        EditLock::displayName)
        .containsExactly(expected.recordId(), expected.departmentId(), expected.userId(),
            expected.displayName());
    assertThat(actual.token().equals(expected.token())).isTrue();
    assertThat(actual.expiresAt().toEpochMilli()).isEqualTo(expected.expiresAt().toEpochMilli());
  }
}
