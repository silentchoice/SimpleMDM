package com.example.mdm.record;

import static com.example.mdm.record.RecordDraftCommand.ChildRowCommand;
import static com.example.mdm.record.RecordDraftCommand.ChildRows;
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
import com.example.mdm.metadata.FieldDefinition;
import com.example.mdm.metadata.FieldType;
import com.example.mdm.metadata.FieldValueValidator;
import com.example.mdm.metadata.MasterType;
import com.example.mdm.metadata.MetadataRepository;
import com.example.mdm.metadata.MetadataStatus;
import com.example.mdm.metadata.SubType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class RecordDraftServiceTest {
  private final RecordRepository records = new MemoryRecordRepository();
  private final MetadataRepository metadata = Mockito.mock(MetadataRepository.class);
  private final CodeRuleService codes = Mockito.mock(CodeRuleService.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final UserPrincipal editor = new UserPrincipal(12L, "editor", "Editor",
      new DepartmentPrincipal(7L, "D7", "Department 7"), List.of(Role.DEPT_EDITOR));
  private RecordDraftService service;

  @BeforeEach void setUp() {
    service = new RecordDraftService(records, metadata, new FieldValueValidator(), codes,
        authorization, Clock.fixed(Instant.parse("2026-08-05T08:00:00Z"), ZoneOffset.UTC));
    when(authorization.requireRole(Role.DEPT_EDITOR)).thenReturn(editor);
    when(metadata.findAssignedMasterType(7L))
        .thenReturn(new MasterType(9L, "CUS", "Customer", MetadataStatus.ACTIVE));
    when(metadata.findMasterFields(7L, 9L)).thenReturn(List.of(
        field(1L, 9L, "name", true)));
    when(metadata.findSubTypes(7L, 9L)).thenReturn(List.of(
        new SubType(31L, 9L, "CONTACT", "Contact", MetadataStatus.ACTIVE)));
    when(metadata.findSubFields(7L, 31L)).thenReturn(List.of(
        field(2L, 31L, "contact", true)));
    when(codes.allocate(9L, java.time.LocalDate.of(2026, 8, 5)))
        .thenReturn("CUS-20260805-0001");
  }

  @Test void newDraftAllocatesCodeAndValidatesTheCompleteMasterAndEveryChildRow() {
    var command = new RecordDraftCommand(null, 9L, 0L, RecordAction.CREATE,
        Map.of("name", "North Supplier"), List.of(new ChildRows(31L, List.of(
            new ChildRowCommand(null, 0, Map.of("contact", "Li"))))), null);

    RecordDraft saved = service.create(command);

    assertThat(saved.recordCode()).isEqualTo("CUS-20260805-0001");
    assertThat(saved.departmentId()).isEqualTo(7L);
    assertThat(saved.createdBy()).isEqualTo(12L);
    assertThat(saved.status()).isEqualTo(RecordStatus.DRAFT);
    assertThat(saved.children().get(0).rows().get(0).values())
        .containsExactlyEntriesOf(Map.of("contact", "Li"));
    verify(authorization).requireDepartment(7L);
  }

  @Test void aMissingRequiredValueInAnyChildRowRejectsTheWholeDraft() {
    var command = new RecordDraftCommand(null, 9L, 0L, RecordAction.CREATE,
        Map.of("name", "North Supplier"), List.of(new ChildRows(31L, List.of(
            new ChildRowCommand(null, 0, Map.of())))), null);

    assertThatThrownBy(() -> service.create(command))
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(exception.getMessage()).contains("contact");
        });
  }

  @Test void unknownOrInactiveSubtypeIsRejectedBeforeItsValuesCanBeSaved() {
    var command = new RecordDraftCommand(null, 9L, 0L, RecordAction.CREATE,
        Map.of("name", "North Supplier"), List.of(new ChildRows(99L, List.of(
            new ChildRowCommand(null, 0, Map.of("contact", "Li"))))), null);

    assertThatThrownBy(() -> service.create(command))
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(exception.getMessage()).contains("Unknown or inactive sub type: 99");
        });
  }

  @Test void repeatedDraftSavesKeepThePathIdentityCodeAndOriginalTarget() {
    var memory = (MemoryRecordRepository) records;
    memory.formal = formal(81L, 3L);
    var created = service.create(updateCommand(81L, 3L, "North Supplier"));

    var first = service.update(created.id(), updateCommand(81L, 3L, "North Supplier Ltd"));
    var second = service.update(created.id(), updateCommand(81L, 3L, "North Supplier Group"));

    assertThat(first.id()).isEqualTo(created.id());
    assertThat(second.id()).isEqualTo(created.id());
    assertThat(second.recordId()).isEqualTo(81L);
    assertThat(second.recordCode()).isEqualTo("CUS-EXISTING");
    assertThat(second.masterValues()).containsEntry("name", "North Supplier Group");

    assertThatThrownBy(() -> service.update(created.id(),
        updateCommand(82L, 3L, "Path substitution")))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test void pendingDraftCannotBeEdited() {
    var memory = (MemoryRecordRepository) records;
    memory.draft = draft(21L, RecordStatus.PENDING, RecordAction.CREATE, null);

    assertThatThrownBy(() -> service.update(21L, new RecordDraftCommand(null, 9L, 0L,
        RecordAction.CREATE, Map.of("name", "Changed"), List.of(), null)))
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.getMessage()).isEqualTo("Draft is no longer editable");
        });
  }

  @Test void publicCreateRejectsDeleteDrafts() {
    assertThatThrownBy(() -> service.create(new RecordDraftCommand(81L, 9L, 3L,
        RecordAction.DELETE, Map.of("name", "North Supplier"), List.of(), "Duplicate")))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test void updateAndLogicalDeleteRejectAFormalRecordThatIsNotActive() {
    var memory = (MemoryRecordRepository) records;
    memory.formal = formal(81L, 3L, "DELETED");

    assertThatThrownBy(() -> service.create(updateCommand(81L, 3L, "North Supplier")))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> service.logicalDelete(81L, "Duplicate supplier"))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test void duplicateExistingChildIdsAreRejectedAcrossTheEntireDraft() {
    var memory = (MemoryRecordRepository) records;
    memory.formal = formal(81L, 3L);
    var command = new RecordDraftCommand(81L, 9L, 3L, RecordAction.UPDATE,
        Map.of("name", "North Supplier"), List.of(new ChildRows(31L, List.of(
            new ChildRowCommand(101L, 0, Map.of("contact", "Li")),
            new ChildRowCommand(101L, 1, Map.of("contact", "Wang"))))), null);

    assertThatThrownBy(() -> service.create(command))
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(exception.getMessage()).contains("Duplicate child record id: 101");
        });
  }

  @Test void logicalDeleteRequiresANonBlankReasonAndCopiesTheCurrentFormalSnapshot() {
    var memory = (MemoryRecordRepository) records;
    memory.formal = formal(81L, 3L);

    assertThatThrownBy(() -> service.logicalDelete(81L, "  "))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));

    var deletion = service.logicalDelete(81L, "Duplicate supplier");
    assertThat(deletion.action()).isEqualTo(RecordAction.DELETE);
    assertThat(deletion.deleteReason()).isEqualTo("Duplicate supplier");
    assertThat(deletion.baseVersion()).isEqualTo(3L);
    assertThat(deletion.masterValues()).containsEntry("name", "North Supplier");
  }

  @Test void rejectedDraftCanBeCopiedToANewEditableDraft() {
    var memory = (MemoryRecordRepository) records;
    memory.draft = draft(21L, RecordStatus.REJECTED, RecordAction.CREATE, null);

    var copy = service.copyRejected(21L);

    assertThat(copy.id()).isNotEqualTo(21L);
    assertThat(copy.status()).isEqualTo(RecordStatus.DRAFT);
    assertThat(copy.recordCode()).isEqualTo("CUS-20260805-0001");
    assertThat(copy.masterValues()).containsEntry("name", "North Supplier");
  }

  @Test void copyingARejectedDeleteRechecksThatTheCurrentFormalVersionIsActive() {
    var memory = (MemoryRecordRepository) records;
    memory.draft = draft(21L, RecordStatus.REJECTED, RecordAction.DELETE, 81L);
    memory.formal = formal(81L, 0L, "DELETED");

    assertThatThrownBy(() -> service.copyRejected(21L))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test void templateAndFormalRecordIdsCannotCrossTheAuthenticatedDepartment() {
    var memory = (MemoryRecordRepository) records;
    memory.foreignRecord = true;

    assertThatThrownBy(() -> service.create(updateCommand(81L, 3L, "North Supplier")))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(codes, never()).allocate(Mockito.anyLong(), Mockito.any());
  }

  private RecordDraftCommand updateCommand(long recordId, long version, String name) {
    return new RecordDraftCommand(recordId, 9L, version, RecordAction.UPDATE, Map.of("name", name),
        List.of(new ChildRows(31L, List.of(
            new ChildRowCommand(101L, 0, Map.of("contact", "Li"))))), null);
  }

  private RecordView formal(long id, long version) {
    return formal(id, version, "ACTIVE");
  }

  private RecordView formal(long id, long version, String status) {
    return new RecordView(id, 9L, 7L, "CUS-EXISTING", Map.of("name", "North Supplier"),
        List.of(new RecordView.ChildRows(31L, List.of(
            new RecordView.ChildRow(101L, 0, Map.of("contact", "Li"))))),
        version, status);
  }

  private RecordDraft draft(long id, RecordStatus status, RecordAction action, Long recordId) {
    return new RecordDraft(id, recordId, 9L, 7L, "CUS-20260805-0001", action, 0L,
        Map.of("name", "North Supplier"), List.of(), status, 12L, null);
  }

  private FieldDefinition field(long id, long owner, String code, boolean required) {
    return new FieldDefinition(id, owner, code, code, FieldType.TEXT, required, List.of(), false,
        0, MetadataStatus.ACTIVE);
  }

  private static final class MemoryRecordRepository implements RecordRepository {
    private long nextId = 100;
    private RecordDraft draft;
    private RecordView formal;
    private boolean foreignRecord;

    @Override public RecordDraft saveDraft(long departmentId, long actorId, RecordDraft value) {
      draft = new RecordDraft(value.id() == 0 ? nextId++ : value.id(), value.recordId(),
          value.masterTypeId(), departmentId, value.recordCode(), value.action(), value.baseVersion(),
          value.masterValues(), value.children(), value.status(), actorId, value.deleteReason());
      return draft;
    }

    @Override public RecordDraft findDraft(long departmentId, long draftId) {
      if (draft == null || draft.id() != draftId) throw BusinessException.notFound("Draft");
      if (draft.departmentId() != departmentId) throw BusinessException.forbidden();
      return draft;
    }

    @Override public RecordView findRecord(long departmentId, long recordId) {
      if (foreignRecord) throw BusinessException.forbidden();
      if (formal == null || formal.id() != recordId) throw BusinessException.notFound("Record");
      if (formal.departmentId() != departmentId) throw BusinessException.forbidden();
      return formal;
    }

    @Override public RecordView activate(long draftId, long actorId) {
      throw new UnsupportedOperationException();
    }

    @Override public void retainLatestHistory(long recordId, int keep) {}
  }
}
