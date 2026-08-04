package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

class MetadataApprovalApplicationServiceTest {
  private final MetadataApprovalRepository approvals = Mockito.mock(MetadataApprovalRepository.class);
  private final MetadataRepository metadata = Mockito.mock(MetadataRepository.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final FieldStructureValidator validator = Mockito.mock(FieldStructureValidator.class);
  private final ObjectMapper json = new ObjectMapper();
  private MetadataApprovalApplicationService service;

  @BeforeEach void setUp() {
    service = new MetadataApprovalApplicationService(approvals, metadata, authorization, validator, json);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver(23, 7));
  }

  @Test void approvalLocksTaskThenAssignmentAndAppliesOrderedSnapshot() throws Exception {
    var before = List.of(field(1, 41, "old", 1));
    var after = List.of(field(0, 41, "second", 2), field(0, 41, "first", 1));
    when(approvals.lock(9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", fingerprint(before), before),
        envelope(7, 41, "MASTER_FIELDS", fingerprint(before), after), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(before);

    service.approve(9, "looks good");

    var order = inOrder(approvals, metadata);
    order.verify(approvals).lock(9);
    order.verify(metadata).lockTemplateAssignment(7, 41);
    order.verify(metadata).findMasterFields(7, 41);
    order.verify(metadata).replaceMasterFields(7, 41, after);
    order.verify(approvals).approve(9, 23, "looks good");
  }

  @Test void crossDepartmentAndNonPendingTasksAreRejectedWithoutWrites() throws Exception {
    when(approvals.lock(9)).thenReturn(task(9, 8, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    assertStatus(() -> service.approve(9, null), HttpStatus.FORBIDDEN);
    when(approvals.lock(10)).thenReturn(task(10, 7, "MASTER_FIELDS", 41, "{}", "{}", "APPROVED"));
    assertStatus(() -> service.approve(10, null), HttpStatus.CONFLICT);
    verify(metadata, never()).replaceMasterFields(any(Long.class), any(Long.class), any());
  }

  @Test void staleBaseFingerprintIsConflictAndNeverApplies() throws Exception {
    var current = List.of(field(1, 41, "changed", 1));
    var submittedBase = List.of(field(1, 41, "old", 1));
    when(approvals.lock(9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", fingerprint(submittedBase), submittedBase),
        envelope(7, 41, "MASTER_FIELDS", fingerprint(submittedBase), submittedBase), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(current);

    assertStatus(() -> service.approve(9, null), HttpStatus.CONFLICT);
    verify(metadata, never()).replaceMasterFields(any(Long.class), any(Long.class), any());
    verify(approvals, never()).approve(any(Long.class), any(Long.class), any());
  }

  @Test void malformedOrMismatchedImmutableEnvelopeIsBadRequest() {
    when(approvals.lock(9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    assertStatus(() -> service.approve(9, null), HttpStatus.BAD_REQUEST);
  }

  @Test void rejectionRequiresReasonAndDoesNotTouchActiveMetadata() {
    when(approvals.lock(9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    assertStatus(() -> service.reject(9, " "), HttpStatus.BAD_REQUEST);

    service.reject(9, "not complete");

    verify(approvals).reject(9, 23, "not complete");
    verify(metadata, never()).lockTemplateAssignment(any(Long.class), any(Long.class));
    verify(metadata, never()).replaceMasterFields(any(Long.class), any(Long.class), any());
  }

  @Test void approvalAndRejectionAreTransactional() throws Exception {
    assertThat(MetadataApprovalApplicationService.class.getMethod("approve", long.class, String.class)
        .isAnnotationPresent(Transactional.class)).isTrue();
    assertThat(MetadataApprovalApplicationService.class.getMethod("reject", long.class, String.class)
        .isAnnotationPresent(Transactional.class)).isTrue();
  }

  private MetadataApprovalRepository.ApprovalTask task(long id, long department, String kind,
      long entity, String before, String after, String status) {
    return new MetadataApprovalRepository.ApprovalTask(id, department, kind, entity, before, after, status);
  }
  private UserPrincipal approver(long id, long department) {
    return new UserPrincipal(id, "approver", "Approver",
        new DepartmentPrincipal(department, "D" + department, "Department"), List.of(Role.DEPT_APPROVER));
  }
  private FieldDefinition field(long id, long owner, String code, int order) {
    return new FieldDefinition(id, owner, code, code, FieldType.TEXT, false, List.of(), false, order,
        MetadataStatus.ACTIVE);
  }
  private String envelope(long department, long template, String kind, String base, Object definitions)
      throws Exception {
    return json.writeValueAsString(java.util.Map.of("schemaVersion", 1, "departmentId", department,
        "templateId", template, "entityKind", kind, "baseFingerprint", base,
        "orderedDefinitions", definitions));
  }
  private String fingerprint(Object definitions) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(json.writeValueAsString(definitions).getBytes(StandardCharsets.UTF_8)));
  }
  private void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
    assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
        exception -> assertThat(exception.status()).isEqualTo(status));
  }
}
