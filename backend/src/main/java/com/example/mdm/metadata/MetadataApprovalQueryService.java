package com.example.mdm.metadata;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.example.mdm.record.RecordApprovalRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetadataApprovalQueryService {
  private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
  private final MetadataApprovalRepository approvals;
  private final RecordApprovalRepository recordApprovals;
  private final AuthorizationService authorization;

  @Autowired
  public MetadataApprovalQueryService(MetadataApprovalRepository approvals,
      AuthorizationService authorization, ObjectProvider<RecordApprovalRepository> recordApprovals) {
    this.approvals = approvals;
    this.recordApprovals = recordApprovals.getIfAvailable();
    this.authorization = authorization;
  }

  public MetadataApprovalQueryService(MetadataApprovalRepository approvals,
      AuthorizationService authorization) {
    this.approvals = approvals;
    this.recordApprovals = null;
    this.authorization = authorization;
  }

  public List<MetadataApprovalRepository.ApprovalTaskView> list(String status) {
    UserPrincipal actor = approver();
    String normalized = normalizeStatus(status);
    return approvals.list(actor.department().id(), normalized);
  }

  public List<MetadataApprovalRepository.ApprovalTaskView> list(String status, String taskType) {
    UserPrincipal actor = approver();
    String normalized = normalizeStatus(status);
    String type = taskType == null ? "METADATA" : taskType.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("METADATA", "RECORD").contains(type)) {
      throw BusinessException.badRequest("Invalid approval task type");
    }
    if ("RECORD".equals(type)) {
      if (recordApprovals == null) throw BusinessException.badRequest("Invalid approval task type");
      return recordApprovals.list(actor.department().id(), normalized);
    }
    return approvals.list(actor.department().id(), normalized);
  }

  private String normalizeStatus(String status) {
    String normalized = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(normalized)) {
      throw BusinessException.badRequest("Invalid approval status");
    }
    return normalized;
  }

  public MetadataApprovalRepository.ApprovalTaskView detail(long taskId) {
    UserPrincipal actor = approver();
    return approvals.detail(actor.department().id(), taskId);
  }

  public MetadataApprovalRepository.ApprovalTaskView detail(long taskId, String taskType) {
    UserPrincipal actor = approver();
    String type = taskType == null ? "METADATA" : taskType.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("METADATA", "RECORD").contains(type)) {
      throw BusinessException.badRequest("Invalid approval task type");
    }
    if ("RECORD".equals(type)) {
      if (recordApprovals == null) throw BusinessException.badRequest("Invalid approval task type");
      return recordApprovals.detail(actor.department().id(), taskId);
    }
    return approvals.detail(actor.department().id(), taskId);
  }

  private UserPrincipal approver() {
    UserPrincipal actor = authorization.requireRole(Role.DEPT_APPROVER);
    if (actor.department() == null) {
      throw BusinessException.forbidden();
    }
    authorization.requireDepartment(actor.department().id());
    return actor;
  }
}
