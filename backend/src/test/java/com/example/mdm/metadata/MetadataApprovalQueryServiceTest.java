package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MetadataApprovalQueryServiceTest {
  private final MetadataApprovalRepository approvals = Mockito.mock(MetadataApprovalRepository.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final MetadataApprovalQueryService service =
      new MetadataApprovalQueryService(approvals, authorization);

  @Test void listDerivesDepartmentFromApproverAndNormalizesStatus() {
    var actor = approver();
    var task = task(91, "PENDING");
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(actor);
    when(approvals.list(7, "PENDING")).thenReturn(List.of(task));

    assertThat(service.list("pending")).containsExactly(task);
    verify(authorization).requireDepartment(7);
    verify(approvals).list(7, "PENDING");
  }

  @Test void detailDerivesDepartmentFromApprover() {
    var actor = approver();
    var task = task(91, "PENDING");
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(actor);
    when(approvals.detail(7, 91)).thenReturn(task);

    assertThat(service.detail(91)).isEqualTo(task);
    verify(authorization).requireDepartment(7);
    verify(approvals).detail(7, 91);
  }

  private UserPrincipal approver() {
    return new UserPrincipal(23, "reviewer", "Reviewer",
        new DepartmentPrincipal(7, "SALES", "Sales"), List.of(Role.DEPT_APPROVER));
  }

  private MetadataApprovalRepository.ApprovalTaskView task(long id, String status) {
    return new MetadataApprovalRepository.ApprovalTaskView(id, "MASTER_FIELDS", 41, status,
        "{}", "{}", 12, null, null, LocalDateTime.of(2026, 8, 4, 9, 30), null);
  }
}
