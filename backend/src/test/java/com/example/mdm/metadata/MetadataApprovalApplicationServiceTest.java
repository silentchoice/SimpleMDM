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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class MetadataApprovalApplicationServiceTest {
  private final MetadataApprovalRepository approvals = Mockito.mock(MetadataApprovalRepository.class);
  private final MetadataRepository metadata = Mockito.mock(MetadataRepository.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final FieldStructureValidator validator = Mockito.spy(new FieldStructureValidator());
  private final ObjectMapper json = new ObjectMapper();
  private MetadataApprovalApplicationService service;

  @BeforeEach void setUp() {
    service = new MetadataApprovalApplicationService(approvals, metadata, authorization, validator, json);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(approver(23, 7));
  }

  @Test void approvalLocksTaskThenAssignmentAndAppliesOrderedSnapshot() throws Exception {
    var before = List.of(field(1, 41, "old", 1));
    var after = List.of(field(0, 41, "second", 2), field(0, 41, "first", 1));
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", fingerprint(before), before),
        envelope(7, 41, "MASTER_FIELDS", fingerprint(before), after), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(before);

    String comment = "x".repeat(1000);
    service.approve(9, comment);

    var order = inOrder(approvals, metadata);
    order.verify(approvals).lock(7, 9);
    order.verify(metadata).lockTemplateAssignment(7, 41);
    order.verify(metadata).findMasterFields(7, 41);
    order.verify(metadata).replaceMasterFields(7, 41, after);
    order.verify(approvals).approve(7, 9, 23, comment);
  }

  @Test void firstMasterFieldSnapshotCanBeApprovedFromAnEmptyActiveVersion() throws Exception {
    var before = List.<FieldDefinition>of();
    var after = List.of(field(0, 41, "serial", 0));
    String base = fingerprint(before);
    when(approvals.lock(7, 91)).thenReturn(task(91, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", base, before),
        envelope(7, 41, "MASTER_FIELDS", base, after), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(before);

    service.approve(91, null);

    verify(metadata).replaceMasterFields(7, 41, after);
    verify(approvals).approve(7, 91, 23, null);
  }

  @Test void firstSubtypeSnapshotCanBeApprovedFromAnEmptyActiveVersion() throws Exception {
    var before = List.<SubType>of();
    var after = List.of(new SubType(0, 41, "device", "Device", MetadataStatus.ACTIVE));
    String base = fingerprint(before);
    when(approvals.lock(7, 92)).thenReturn(task(92, 7, "SUB_TYPES", 41,
        envelope(7, 41, "SUB_TYPES", base, before),
        envelope(7, 41, "SUB_TYPES", base, after), "PENDING"));
    when(metadata.findSubTypes(7, 41)).thenReturn(before);

    service.approve(92, null);

    verify(metadata).replaceSubTypes(7, 41, after);
    verify(approvals).approve(7, 92, 23, null);
  }

  @Test void firstSubfieldSnapshotCanBeApprovedFromAnEmptyActiveVersion() throws Exception {
    var before = List.<FieldDefinition>of();
    var after = List.of(field(0, 55, "model", 0));
    String base = fingerprint(before);
    when(approvals.lock(7, 93)).thenReturn(task(93, 7, "SUB_FIELDS", 55,
        envelope(7, 41, "SUB_FIELDS", base, before),
        envelope(7, 41, "SUB_FIELDS", base, after), "PENDING"));
    when(approvals.requireSubTypeTemplate(7, 55)).thenReturn(41L);
    when(metadata.findSubFields(7, 55)).thenReturn(before);

    service.approve(93, null);

    verify(metadata).replaceSubFields(7, 55, after);
    verify(approvals).approve(7, 93, 23, null);
  }

  @Test void crossDepartmentAndNonPendingTasksAreRejectedWithoutWrites() throws Exception {
    when(approvals.lock(7, 9)).thenReturn(task(9, 8, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    assertStatus(() -> service.approve(9, null), HttpStatus.FORBIDDEN);
    when(approvals.lock(7, 10)).thenReturn(task(10, 7, "MASTER_FIELDS", 41, "{}", "{}", "APPROVED"));
    assertStatus(() -> service.approve(10, null), HttpStatus.CONFLICT);
    verify(metadata, never()).replaceMasterFields(any(Long.class), any(Long.class), any());
  }

  @Test void nonMetadataTaskIsNotFoundForApproveAndRejectWithoutTransition() {
    when(approvals.lock(7, 9)).thenReturn(
        task(9, 7, "USER_ACCESS", 41, "{}", "{}", "PENDING"));

    assertStatus(() -> service.approve(9, null), HttpStatus.NOT_FOUND);
    assertStatus(() -> service.reject(9, "reason"), HttpStatus.NOT_FOUND);
    verify(approvals, never()).approve(any(Long.class), any(Long.class), any(Long.class), any());
    verify(approvals, never()).reject(any(Long.class), any(Long.class), any(Long.class), any());
  }

  @Test void overlongCommentAndReasonAreBadRequestBeforeTaskStorage() {
    String overlong = "x".repeat(1001);

    assertStatus(() -> service.approve(9, overlong), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.reject(9, overlong), HttpStatus.BAD_REQUEST);
    verify(approvals, never()).lock(any(Long.class), any(Long.class));
  }

  @Test void staleBaseFingerprintIsConflictAndNeverApplies() throws Exception {
    var current = List.of(field(1, 41, "changed", 1));
    var submittedBase = List.of(field(1, 41, "old", 1));
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", fingerprint(submittedBase), submittedBase),
        envelope(7, 41, "MASTER_FIELDS", fingerprint(submittedBase), submittedBase), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(current);

    assertStatus(() -> service.approve(9, null), HttpStatus.CONFLICT);
    verify(metadata, never()).replaceMasterFields(any(Long.class), any(Long.class), any());
    verify(approvals, never()).approve(any(Long.class), any(Long.class), any(Long.class), any());
  }

  @Test void malformedOrMismatchedImmutableEnvelopeIsBadRequest() {
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    assertStatus(() -> service.approve(9, null), HttpStatus.BAD_REQUEST);
  }

  @Test void malformedFieldMemberWithNullCodeIsBadRequestInsteadOfServerError() throws Exception {
    var malformed = List.of(fieldWithCode(null, 41));
    String base = fingerprint(malformed);
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", base, malformed),
        envelope(7, 41, "MASTER_FIELDS", base, malformed), "PENDING"));
    assertStatus(() -> service.approve(9, null), HttpStatus.BAD_REQUEST);
  }

  @Test void approvalDecodeRejectsAFieldDisplayNameLongerThan128Characters() throws Exception {
    var before = List.of(field(1, 41, "serial", 0));
    var after = List.of(new FieldDefinition(0, 41, "serial", "x".repeat(129), FieldType.TEXT,
        false, List.of(), false, 0, MetadataStatus.ACTIVE));
    String base = fingerprint(before);
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", base, before),
        envelope(7, 41, "MASTER_FIELDS", base, after), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(before);

    assertStatus(() -> service.approve(9, null), HttpStatus.BAD_REQUEST);
    verify(metadata, never()).replaceMasterFields(any(Long.class), any(Long.class), any());
    verify(approvals, never()).approve(any(Long.class), any(Long.class), any(Long.class), any());
  }

  @Test void subtypeApprovalPreservesOrderedDefinitionsForRepositoryDiff() throws Exception {
    var before = List.of(new SubType(55, 41, "retained", "Old", MetadataStatus.ACTIVE));
    var after = List.of(new SubType(55, 41, "retained", "New", MetadataStatus.ACTIVE),
        new SubType(0, 41, "added", "Added", MetadataStatus.ACTIVE));
    String base = fingerprint(before);
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "SUB_TYPES", 41,
        envelope(7, 41, "SUB_TYPES", base, before),
        envelope(7, 41, "SUB_TYPES", base, after), "PENDING"));
    when(metadata.findSubTypes(7, 41)).thenReturn(before);
    service.approve(9, null);
    verify(metadata).replaceSubTypes(7, 41, after);
    verify(approvals).approve(7, 9, 23, null);
  }

  @Test void subfieldApprovalValidatesSubtypeTemplateAndAppliesOnlyThatSubtype() throws Exception {
    var before = List.of(field(1, 55, "old", 1));
    var after = List.of(field(0, 55, "new", 1));
    String base = fingerprint(before);
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "SUB_FIELDS", 55,
        envelope(7, 41, "SUB_FIELDS", base, before),
        envelope(7, 41, "SUB_FIELDS", base, after), "PENDING"));
    when(approvals.requireSubTypeTemplate(7, 55)).thenReturn(41L);
    when(metadata.findSubFields(7, 55)).thenReturn(before);
    service.approve(9, null);
    verify(metadata).lockTemplateAssignment(7, 41);
    verify(metadata).replaceSubFields(7, 55, after);
  }

  @Test void missingTaskIsNotFoundAndRepeatedRejectionIsConflict() {
    when(approvals.lock(7, 404)).thenThrow(BusinessException.notFound("Approval task"));
    assertStatus(() -> service.approve(404, null), HttpStatus.NOT_FOUND);
    when(approvals.lock(7, 10)).thenReturn(task(10, 7, "MASTER_FIELDS", 41, "{}", "{}", "REJECTED"));
    assertStatus(() -> service.reject(10, "again"), HttpStatus.CONFLICT);
  }

  @Test void entityAndTemplateMismatchIsBadRequest() throws Exception {
    var fields = List.of(field(1, 41, "old", 1));
    String base = fingerprint(fields);
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 42, "MASTER_FIELDS", base, fields),
        envelope(7, 42, "MASTER_FIELDS", base, fields), "PENDING"));
    assertStatus(() -> service.approve(9, null), HttpStatus.BAD_REQUEST);
  }

  @Test void taskTransitionFailureEscapesTransactionalBoundaryForRollback() throws Exception {
    var before = List.of(field(1, 41, "old", 1));
    var after = List.of(field(0, 41, "new", 1));
    String base = fingerprint(before);
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41,
        envelope(7, 41, "MASTER_FIELDS", base, before),
        envelope(7, 41, "MASTER_FIELDS", base, after), "PENDING"));
    when(metadata.findMasterFields(7, 41)).thenReturn(before);
    Mockito.doThrow(new BusinessException(HttpStatus.CONFLICT, "transition lost"))
        .when(approvals).approve(7, 9, 23, null);
    var transactions = new RecordingTransactionManager();
    var proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(new TransactionInterceptor(transactions,
        new AnnotationTransactionAttributeSource()));
    var transactionalService = (MetadataApprovalApplicationService) proxyFactory.getProxy();

    assertStatus(() -> transactionalService.approve(9, null), HttpStatus.CONFLICT);
    verify(metadata).replaceMasterFields(7, 41, after);
    assertThat(transactions.rolledBack).isTrue();
    assertThat(transactions.committed).isFalse();
  }

  @Test void rejectionRequiresReasonAndDoesNotTouchActiveMetadata() {
    when(approvals.lock(7, 9)).thenReturn(task(9, 7, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    assertStatus(() -> service.reject(9, " "), HttpStatus.BAD_REQUEST);

    String reason = "x".repeat(1000);
    service.reject(9, reason);

    verify(approvals).reject(7, 9, 23, reason);
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
  private FieldDefinition fieldWithCode(String code, long owner) {
    return new FieldDefinition(1, owner, code, "name", FieldType.TEXT, false, List.of(), false, 1,
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

  private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    private boolean committed;
    private boolean rolledBack;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
  }
}
