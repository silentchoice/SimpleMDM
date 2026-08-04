package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class MetadataServiceTest {
  private final MetadataRepository metadata = Mockito.mock(MetadataRepository.class);
  private final MetadataApprovalRepository approvals = Mockito.mock(MetadataApprovalRepository.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final FieldStructureValidator validator = Mockito.mock(FieldStructureValidator.class);
  private final ObjectMapper json = new ObjectMapper();
  private MetadataService service;

  @BeforeEach void setUp() {
    service = new MetadataService(metadata, approvals, authorization, validator, json);
  }

  @Test void masterFieldSubmissionDerivesDepartmentAndStoresLiteralSnapshots() throws Exception {
    var editor = editor(12L, 7L);
    var before = List.of(field(1L, 41L, "old", 1));
    var after = List.of(field(0L, 41L, "new", 2));
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor);
    when(metadata.findMasterFields(7L, 41L)).thenReturn(before);
    when(approvals.submit(any())).thenReturn(99L);

    assertThat(service.submitMasterFields(after)).isEqualTo(99L);

    verify(authorization).requireDepartment(7L);
    verify(metadata).requireAssignment(7L, 41L);
    var request = ArgumentCaptor.forClass(MetadataChangeRequest.class);
    verify(approvals).submit(request.capture());
    assertThat(request.getValue().departmentId()).isEqualTo(7L);
    assertThat(request.getValue().submittedBy()).isEqualTo(12L);
    assertThat(request.getValue().entityKind()).isEqualTo("MASTER_FIELDS");
    assertThat(request.getValue().entityId()).isEqualTo(41L);
    assertEnvelope(request.getValue().beforeSnapshot(), 7L, 41L, "MASTER_FIELDS", "old");
    assertEnvelope(request.getValue().afterSnapshot(), 7L, 41L, "MASTER_FIELDS", "new");
    verify(metadata, never()).createMasterField(any(Long.class), any());
    assertThat(metadata.findMasterFields(7L, 41L)).containsExactlyElementsOf(before);
  }

  @Test void submissionRequiresDepartmentEditorRole() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenThrow(BusinessException.forbidden());

    assertThatThrownBy(() -> service.submitMasterFields(List.of(field(0, 41, "new", 1))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(approvals, never()).submit(any());
  }

  @Test void submissionRejectsPrincipalWithoutDepartment() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(
        new UserPrincipal(12L, "editor", "Editor", null, List.of(Role.DEPT_EDITOR)));

    assertThatThrownBy(() -> service.submitMasterFields(List.of(field(0, 41, "new", 1))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(approvals, never()).submit(any());
  }

  @Test void submissionRejectsTemplateNotAssignedToEditorsDepartment() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    Mockito.doThrow(BusinessException.notFound("Master type assignment"))
        .when(metadata).requireAssignment(7L, 88L);

    assertThatThrownBy(() -> service.submitSubTypes(List.of(
        new SubType(0L, 88L, "local", "Local", MetadataStatus.ACTIVE))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(approvals, never()).submit(any());
  }

  @Test void subFieldSubmissionRejectsSubtypeFromAnotherDepartment() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    when(approvals.requireSubTypeTemplate(7L, 55L))
        .thenThrow(BusinessException.forbidden());

    assertThatThrownBy(() -> service.submitSubFields(55L, List.of(field(0, 55, "new", 1))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(approvals, never()).submit(any());
  }

  @Test void subFieldSubmissionReturnsNotFoundWhenSubtypeDoesNotExist() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    when(approvals.requireSubTypeTemplate(7L, 404L))
        .thenThrow(BusinessException.notFound("Sub type"));

    assertThatThrownBy(() -> service.submitSubFields(404L, List.of(field(0, 404, "new", 1))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test void rejectsMalformedSubtypeStructuresAndLists() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    var invalid = List.of(
        List.of(new SubType(0, 41, null, "Name", MetadataStatus.ACTIVE)),
        List.of(new SubType(0, 41, "bad-code", "Name", MetadataStatus.ACTIVE)),
        List.of(new SubType(0, 41, "valid", " ", MetadataStatus.ACTIVE)),
        List.of(new SubType(0, 41, "valid", "Name", MetadataStatus.DISABLED)),
        List.of(new SubType(0, 41, "same", "One", MetadataStatus.ACTIVE),
            new SubType(0, 41, "SAME", "Two", MetadataStatus.ACTIVE)));

    for (var types : invalid) {
      assertThatThrownBy(() -> service.submitSubTypes(types))
          .isInstanceOfSatisfying(BusinessException.class,
              exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    verify(approvals, never()).submit(any());
  }

  @Test void rejectsDuplicateFieldCodesAndSortOrders() {
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));

    assertThatThrownBy(() -> service.submitMasterFields(List.of(
        field(0, 41, "same", 1), field(0, 41, "SAME", 2))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> service.submitMasterFields(List.of(
        field(0, 41, "one", 1), field(0, 41, "two", 1))))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test void subTypeSubmissionStoresSnapshotWithoutActiveWrites() {
    var before = List.of(new SubType(1, 41, "old", "Old", MetadataStatus.ACTIVE));
    var after = List.of(new SubType(0, 41, "new", "New", MetadataStatus.ACTIVE));
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    when(metadata.findSubTypes(7L, 41L)).thenReturn(before);
    when(approvals.submit(any())).thenReturn(2L);

    assertThat(service.submitSubTypes(after)).isEqualTo(2L);

    verify(metadata, never()).createSubType(anyLong(), anyLong(), any(), any());
    assertThat(metadata.findSubTypes(7L, 41L)).containsExactlyElementsOf(before);
  }

  @Test void subFieldSubmissionStoresSnapshotWithoutActiveWrites() {
    var before = List.of(field(1, 55, "old", 1));
    var after = List.of(field(0, 55, "new", 2));
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    when(approvals.requireSubTypeTemplate(7L, 55L)).thenReturn(41L);
    when(metadata.findSubFields(7L, 55L)).thenReturn(before);
    when(approvals.submit(any())).thenReturn(3L);

    assertThat(service.submitSubFields(55L, after)).isEqualTo(3L);

    verify(metadata, never()).createSubField(anyLong(), any());
    assertThat(metadata.findSubFields(7L, 55L)).containsExactlyElementsOf(before);
  }

  @Test void validatesEverySubmittedFieldAndPreservesOrder() throws Exception {
    var fields = List.of(field(0, 41, "second", 2), field(0, 41, "first", 1));
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor(12L, 7L));
    when(approvals.submit(any())).thenReturn(1L);

    service.submitMasterFields(fields);

    verify(validator).validate(fields.get(0));
    verify(validator).validate(fields.get(1));
    var request = ArgumentCaptor.forClass(MetadataChangeRequest.class);
    verify(approvals).submit(request.capture());
    var definitions = json.readTree(request.getValue().afterSnapshot()).get("orderedDefinitions");
    assertThat(definitions.get(0).get("code").asText()).isEqualTo("second");
    assertThat(definitions.get(1).get("code").asText()).isEqualTo("first");
  }

  @Test void legacyReadDerivesDepartmentAndNeverUsesUnscopedRepositoryDefaults() {
    var viewer = new UserPrincipal(18L, "viewer", "Viewer",
        new DepartmentPrincipal(7L, "D7", "Department"), List.of(Role.DEPT_VIEWER));
    when(authorization.requireRole(Role.DEPT_EDITOR, Role.DEPT_APPROVER, Role.DEPT_VIEWER))
        .thenReturn(viewer);
    var fields = List.of(field(1, 41, "current", 1));
    when(metadata.findMasterFields(7L, 41L)).thenReturn(fields);

    assertThat(service.masterFields(41L)).containsExactlyElementsOf(fields);

    verify(authorization).requireDepartment(7L);
    verify(metadata).requireAssignment(7L, 41L);
    verify(metadata, never()).findMasterFields(41L);
  }

  private void assertEnvelope(String value, long departmentId, long templateId, String kind,
      String expectedCode) throws Exception {
    var tree = json.readTree(value);
    assertThat(tree.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(tree.get("departmentId").asLong()).isEqualTo(departmentId);
    assertThat(tree.get("templateId").asLong()).isEqualTo(templateId);
    assertThat(tree.get("entityKind").asText()).isEqualTo(kind);
    assertThat(tree.get("orderedDefinitions").get(0).get("code").asText()).isEqualTo(expectedCode);
    assertThat(value).doesNotContain("username", "roles", "credentials", "Editor");
  }

  private UserPrincipal editor(long userId, long departmentId) {
    return new UserPrincipal(userId, "editor", "Editor",
        new DepartmentPrincipal(departmentId, "D" + departmentId, "Department"),
        List.of(Role.DEPT_EDITOR));
  }

  private FieldDefinition field(long id, long ownerId, String code, int sortOrder) {
    return new FieldDefinition(id, ownerId, code, code, FieldType.TEXT, false, List.of(), false,
        sortOrder, MetadataStatus.ACTIVE);
  }
}
