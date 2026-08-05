package com.example.mdm.metadata;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MetadataApprovalQueryService {
  private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
  private final MetadataApprovalRepository approvals;
  private final AuthorizationService authorization;

  public MetadataApprovalQueryService(MetadataApprovalRepository approvals,
      AuthorizationService authorization) {
    this.approvals = approvals;
    this.authorization = authorization;
  }

  public List<MetadataApprovalRepository.ApprovalTaskView> list(String status) {
    UserPrincipal actor = approver();
    String normalized = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(normalized)) {
      throw BusinessException.badRequest("Invalid approval status");
    }
    return approvals.list(actor.department().id(), normalized);
  }

  public MetadataApprovalRepository.ApprovalTaskView detail(long taskId) {
    UserPrincipal actor = approver();
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
