package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.example.mdm.metadata.FieldDefinition;
import com.example.mdm.metadata.FieldType;
import com.example.mdm.metadata.MetadataRepository;
import com.example.mdm.metadata.MetadataStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class RecordVisibilityServiceTest {
  private final MetadataRepository metadata = Mockito.mock(MetadataRepository.class);
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final RecordVisibilityService visibility = new RecordVisibilityService(metadata);
  private final UserPrincipal viewer = new UserPrincipal(12, "viewer", "Viewer",
      new DepartmentPrincipal(7, "SALES", "Sales"), List.of(Role.DEPT_VIEWER));

  @BeforeEach void setUp() {
    Mockito.reset(metadata, authorization);
    when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
        Role.DEPT_VIEWER)).thenReturn(viewer);
    when(metadata.findMasterFields(8, 9)).thenReturn(List.of(
        field(1, 9, "publicName", true), field(2, 9, "taxId", false)));
    when(metadata.findSubFields(8, 31)).thenReturn(List.of(
        field(3, 31, "email", true), field(4, 31, "privateNote", false)));
    when(metadata.findSubFields(8, 32)).thenReturn(List.of(
        field(5, 32, "privateValue", false)));
  }

  @Test void ownDepartmentReceivesTheCompleteMasterAndEveryChildValue() {
    RecordView source = record(7, "ACTIVE");

    assertThat(visibility.filter(source, 7L)).isEqualTo(source);
  }

  @Test void crossDepartmentReceivesOnlySharedValuesAndRowsWithAVisibleValue() {
    RecordView visible = visibility.filter(record(8, "ACTIVE"), 7L);

    assertThat(visible.masterValues()).containsExactlyEntriesOf(Map.of("publicName", "North"));
    assertThat(visible.children()).hasSize(1);
    assertThat(visible.children().get(0).subTypeId()).isEqualTo(31);
    assertThat(visible.children().get(0).rows()).hasSize(1);
    assertThat(visible.children().get(0).rows().get(0).values())
        .containsExactlyEntriesOf(Map.of("email", "shared@example.test"));
  }

  @Test void crossDepartmentFilteringPreservesAnExplicitNullSharedValue() {
    var values = new java.util.LinkedHashMap<String, Object>();
    values.put("publicName", null);
    var source = new RecordView(82, 9, 8, "CUS-NULL", values, List.of(), 1, "ACTIVE");

    RecordView visible = visibility.filter(source, 7L);

    assertThat(visible.masterValues()).containsKey("publicName");
    assertThat(visible.masterValues().get("publicName")).isNull();
  }

  @Test void filteringAndCountsRunAfterVisibilityAndDeletedRowsAreExcludedByDefault() {
    var source = new MemorySource(List.of(
        stored(record(8, "ACTIVE"), LocalDateTime.of(2026, 8, 4, 10, 0)),
        stored(record(8, "DELETED"), LocalDateTime.of(2026, 8, 5, 10, 0))));
    var queries = new RecordQueryService(source, visibility, authorization);

    var secret = queries.list(new RecordQueryService.RecordQuery(9L, null, "CN-SECRET",
        null, false, 0, 20, "updatedAt", "desc"));
    var shared = queries.list(new RecordQueryService.RecordQuery(9L, null, "shared@example.test",
        null, false, 0, 20, "updatedAt", "desc"));
    var includingDeleted = queries.list(new RecordQueryService.RecordQuery(9L, null, null,
        null, true, 0, 20, "updatedAt", "desc"));

    assertThat(secret.totalElements()).isZero();
    assertThat(shared.totalElements()).isEqualTo(1);
    assertThat(shared.content()).allMatch(view -> !view.masterValues().containsKey("taxId"));
    assertThat(includingDeleted.totalElements()).isEqualTo(2);
  }

  @Test void paginationIsCappedSortingIsWhitelistedAndHistoryReadsAtMostThreeSnapshots() {
    var source = new MemorySource(List.of(stored(record(8, "ACTIVE"),
        LocalDateTime.of(2026, 8, 4, 10, 0))));
    var queries = new RecordQueryService(source, visibility, authorization);

    var page = queries.list(new RecordQueryService.RecordQuery(9L, null, null, null,
        false, 0, 500, "recordCode", "asc"));

    assertThat(page.size()).isEqualTo(100);
    assertThatThrownBy(() -> queries.list(new RecordQueryService.RecordQuery(9L, null, null,
        null, false, 0, 20, "field_values; DROP TABLE users", "asc")))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThat(queries.history(81)).hasSize(3).allSatisfy(history -> {
      assertThat(history.masterValues()).doesNotContainKey("taxId");
      assertThat(history.children()).allSatisfy(group -> group.rows().forEach(row ->
          assertThat(row.values()).doesNotContainKey("privateNote")));
    });
    assertThat(source.historyLimit).isEqualTo(3);
  }

  @Test void updatedTimeRangeFiltersTheVisibleResultSetAndRejectsAnInvertedRange() {
    var source = new MemorySource(List.of(
        stored(record(8, "ACTIVE"), LocalDateTime.of(2026, 7, 31, 23, 59)),
        stored(new RecordView(82, 9, 8, "CUS-NEW", Map.of("publicName", "New"),
            List.of(), 1, "ACTIVE"), LocalDateTime.of(2026, 8, 5, 9, 0))));
    var queries = new RecordQueryService(source, visibility, authorization);

    var result = queries.list(new RecordQueryService.RecordQuery(9L, null, null, null, false,
        0, 20, "updatedAt", "asc", LocalDateTime.of(2026, 8, 1, 0, 0),
        LocalDateTime.of(2026, 8, 31, 23, 59)));

    assertThat(result.content()).extracting(RecordView::id).containsExactly(82L);
    assertThatThrownBy(() -> queries.list(new RecordQueryService.RecordQuery(9L, null, null,
        null, false, 0, 20, "updatedAt", "asc", LocalDateTime.of(2026, 9, 1, 0, 0),
        LocalDateTime.of(2026, 8, 1, 0, 0))))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private RecordView record(long departmentId, String status) {
    return new RecordView(81, 9, departmentId, "CUS-20260805-0001",
        Map.of("publicName", "North", "taxId", "CN-SECRET"), List.of(
            new RecordView.ChildRows(31, List.of(
                new RecordView.ChildRow(101, 0,
                    Map.of("email", "shared@example.test", "privateNote", "secret")),
                new RecordView.ChildRow(102, 1, Map.of("privateNote", "hidden")))),
            new RecordView.ChildRows(32, List.of(
                new RecordView.ChildRow(103, 0, Map.of("privateValue", "hidden"))))),
        3, status);
  }

  private RecordQueryService.StoredRecord stored(RecordView view, LocalDateTime updatedAt) {
    return new RecordQueryService.StoredRecord(view, updatedAt);
  }

  private FieldDefinition field(long id, long owner, String code, boolean shared) {
    return new FieldDefinition(id, owner, code, code, FieldType.TEXT, false, List.of(), shared, 0,
        MetadataStatus.ACTIVE);
  }

  private final class MemorySource implements RecordQueryService.RecordSource {
    private final List<RecordQueryService.StoredRecord> records;
    private int historyLimit;

    private MemorySource(List<RecordQueryService.StoredRecord> records) {
      this.records = records;
    }

    @Override public List<RecordQueryService.StoredRecord> records() { return records; }
    @Override public RecordView record(long recordId) {
      return RecordVisibilityServiceTest.this.record(8, "ACTIVE");
    }
    @Override public List<RecordView> history(long recordId, int limit) {
      historyLimit = limit;
      return List.of(RecordVisibilityServiceTest.this.record(8, "ACTIVE"),
          RecordVisibilityServiceTest.this.record(8, "ACTIVE"),
          RecordVisibilityServiceTest.this.record(8, "ACTIVE"));
    }
  }
}
