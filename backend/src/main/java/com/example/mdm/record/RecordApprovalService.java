package com.example.mdm.record;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RecordApprovalService {
  private static final int MAX_REVIEW_LENGTH = 1000;
  private final RecordApprovalRepository approvals;
  private final RecordRepository records;
  private final EditLockStore locks;
  private final AuthorizationService authorization;
  private final RecordSnapshotCodec snapshots;

  public RecordApprovalService(RecordApprovalRepository approvals, RecordRepository records,
      EditLockStore locks, AuthorizationService authorization, RecordSnapshotCodec snapshots) {
    this.approvals = approvals;
    this.records = records;
    this.locks = locks;
    this.authorization = authorization;
    this.snapshots = snapshots;
  }

  @Transactional
  public long submit(long draftId, String lockToken) {
    UserPrincipal actor = editor();
    RecordApprovalRepository.BoundDraft bound = approvals.lockDraft(actor.department().id(), draftId);
    RecordDraft draft = bound.draft();
    if (draft.status() != RecordStatus.DRAFT) throw draftNotEditable();
    if (draft.createdBy() != actor.id()) throw BusinessException.forbidden();
    requireCurrentFormal(draft, bound.formal());
    if (draft.action() != RecordAction.CREATE) requireLock(bound, actor, lockToken);

    String before = bound.formal() == null ? null : snapshots.encode(formalSnapshot(draft, bound.formal()));
    String after = snapshots.encode(draft);
    long taskId = approvals.submit(actor.department().id(), actor.id(), draftId, before, after);
    approvals.markPending(actor.department().id(), draftId, taskId);
    if (draft.action() != RecordAction.CREATE) releaseAfterCommit(draft.recordId(), lockToken);
    return taskId;
  }

  @Transactional
  public RecordView approve(long taskId, String comment) {
    UserPrincipal actor = approver();
    requireLength(comment, "Approval comment");
    RecordApprovalRepository.ApprovalTask task =
        approvals.lockTask(actor.department().id(), taskId);
    requirePendingAndIndependent(task, actor);
    RecordApprovalRepository.BoundDraft bound =
        approvals.lockDraft(actor.department().id(), task.draftId());
    verifyBinding(task, bound);
    requireCurrentFormal(bound.draft(), bound.formal());
    RecordView activated = records.activate(task.draftId(), actor.id());
    approvals.approve(actor.department().id(), taskId, actor.id(), trimToNull(comment));
    return activated;
  }

  @Transactional
  public void reject(long taskId, String reason) {
    UserPrincipal actor = approver();
    if (reason == null || reason.isBlank()) {
      throw BusinessException.badRequest("Rejection reason is required");
    }
    requireLength(reason, "Rejection reason");
    RecordApprovalRepository.ApprovalTask task =
        approvals.lockTask(actor.department().id(), taskId);
    requirePendingAndIndependent(task, actor);
    RecordApprovalRepository.BoundDraft bound =
        approvals.lockDraft(actor.department().id(), task.draftId());
    verifyBinding(task, bound);
    approvals.reject(actor.department().id(), taskId, task.draftId(), actor.id(), reason.trim());
  }

  private void verifyBinding(RecordApprovalRepository.ApprovalTask task,
      RecordApprovalRepository.BoundDraft bound) {
    RecordDraft draft = bound.draft();
    if (task.departmentId() != draft.departmentId() || task.draftId() != draft.id()
        || draft.status() != RecordStatus.PENDING
        || !decode(snapshots.encode(draft)).equals(decode(task.afterSnapshot()))) {
      throw invalidSnapshot();
    }
    if (draft.action() == RecordAction.CREATE) {
      if (task.beforeSnapshot() != null || draft.recordId() != null || bound.formal() != null) {
        throw invalidSnapshot();
      }
      return;
    }
    if (task.beforeSnapshot() == null || bound.formal() == null) throw invalidSnapshot();
    RecordSnapshotCodec.Snapshot before = decode(task.beforeSnapshot());
    if (before.departmentId() != draft.departmentId()
        || before.masterTypeId() != draft.masterTypeId()
        || !Objects.equals(before.recordId(), draft.recordId())
        || !Objects.equals(before.recordCode(), draft.recordCode())
        || before.action() != draft.action() || before.baseVersion() != draft.baseVersion()) {
      throw invalidSnapshot();
    }
  }

  private RecordSnapshotCodec.Snapshot decode(String snapshot) {
    try {
      return snapshots.decode(snapshot);
    } catch (RuntimeException exception) {
      throw invalidSnapshot();
    }
  }

  private void requireCurrentFormal(RecordDraft draft, RecordView formal) {
    if (draft.action() == RecordAction.CREATE) {
      if (draft.recordId() != null || draft.baseVersion() != 0 || formal != null) {
        throw invalidSnapshot();
      }
      return;
    }
    if (draft.recordId() == null || formal == null
        || formal.id() != draft.recordId() || formal.departmentId() != draft.departmentId()
        || formal.masterTypeId() != draft.masterTypeId()) throw invalidSnapshot();
    if (formal.version() != draft.baseVersion()) {
      throw new BusinessException(HttpStatus.CONFLICT, "Record version changed");
    }
    if (!"ACTIVE".equals(formal.status())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Record is no longer active");
    }
  }

  private void requireLock(RecordApprovalRepository.BoundDraft bound, UserPrincipal actor,
      String token) {
    EditLock lock = locks.find(bound.draft().recordId());
    if (lock == null || token == null || !lock.token().equals(token)) {
      throw new BusinessException(HttpStatus.CONFLICT, "Edit lock is no longer held");
    }
    if (lock.departmentId() != actor.department().id() || lock.userId() != actor.id()) {
      throw BusinessException.forbidden();
    }
  }

  private void releaseAfterCommit(long recordId, String token) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("Record submission requires transaction synchronization");
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() {
        try {
          locks.release(recordId, token);
        } catch (RuntimeException unavailable) {
          // The database commit is authoritative; an advisory lock still expires by TTL.
        }
      }
    });
  }

  private RecordDraft formalSnapshot(RecordDraft draft, RecordView formal) {
    List<RecordDraft.ChildRows> children = formal.children().stream().map(group ->
        new RecordDraft.ChildRows(group.subTypeId(), group.rows().stream().map(row ->
            new RecordDraft.ChildRow(row.id(), row.rowOrder(), row.values())).toList())).toList();
    return new RecordDraft(draft.id(), formal.id(), formal.masterTypeId(), formal.departmentId(),
        formal.recordCode(), draft.action(), formal.version(), formal.masterValues(), children,
        RecordStatus.APPROVED, draft.createdBy(), draft.deleteReason());
  }

  private void requirePendingAndIndependent(RecordApprovalRepository.ApprovalTask task,
      UserPrincipal actor) {
    if (task.departmentId() != actor.department().id()) throw BusinessException.forbidden();
    if (!"PENDING".equals(task.status())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Approval task is not pending");
    }
    if (task.submittedBy() == actor.id()) throw BusinessException.forbidden();
  }

  private UserPrincipal editor() {
    UserPrincipal actor = authorization.requireRole(Role.DEPT_EDITOR);
    requireDepartment(actor);
    return actor;
  }

  private UserPrincipal approver() {
    UserPrincipal actor = authorization.requireRole(Role.DEPT_APPROVER);
    requireDepartment(actor);
    return actor;
  }

  private void requireDepartment(UserPrincipal actor) {
    if (actor.department() == null) throw BusinessException.forbidden();
    authorization.requireDepartment(actor.department().id());
  }

  private void requireLength(String value, String label) {
    if (value != null && value.length() > MAX_REVIEW_LENGTH) {
      throw BusinessException.badRequest(label + " must not exceed 1000 characters");
    }
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private BusinessException invalidSnapshot() {
    return BusinessException.badRequest("Invalid record snapshot");
  }

  private BusinessException draftNotEditable() {
    return new BusinessException(HttpStatus.CONFLICT, "Draft is no longer editable");
  }
}
