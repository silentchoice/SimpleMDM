package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class JdbcRecordRepositoryTest {
  private final NamedParameterJdbcTemplate jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
  private final JdbcTemplate positional = Mockito.mock(JdbcTemplate.class);
  private final ObjectMapper json = new ObjectMapper();
  private final JdbcRecordRepository repository = new JdbcRecordRepository(jdbc, json,
      new RecordSnapshotCodec(json));

  JdbcRecordRepositoryTest() {
    when(jdbc.getJdbcTemplate()).thenReturn(positional);
  }

  @Test void repeatedDraftSaveGuardsTheDepartmentAndReplacesEveryChildRow() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    var draft = draft(17L, RecordStatus.DRAFT);

    RecordDraft saved = repository.saveDraft(7L, 12L, draft);

    assertThat(saved).isEqualTo(draft);
    var sql = ArgumentCaptor.forClass(String.class);
    var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc, Mockito.times(2)).update(sql.capture(), parameters.capture());
    assertThat(sql.getAllValues().get(0)).contains("UPDATE master_record_drafts",
        "department_id=:department", "status='DRAFT'");
    assertThat(((MapSqlParameterSource) parameters.getAllValues().get(0)).getValue("values"))
        .isEqualTo("{\"name\":\"North Supplier\"}");
    assertThat(sql.getAllValues().get(1)).contains("DELETE FROM sub_record_drafts",
        "master_draft_id=:draft");
    verify(positional).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
  }

  @SuppressWarnings("unchecked")
  @Test void draftReadsAreDepartmentScopedAndDoNotExposeForeignRows() {
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1, 0);

    assertThatThrownBy(() -> repository.findDraft(7L, 91L))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> repository.findDraft(7L, 404L))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));

    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, Mockito.times(2)).query(sql.capture(), anyMap(), any(RowMapper.class));
    assertThat(sql.getAllValues()).allSatisfy(value ->
        assertThat(value).contains("department_id=:department", "id=:id"));
  }

  @SuppressWarnings("unchecked")
  @Test void activationLocksTheDraftBeforeRejectingAnUnknownId() {
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);

    assertThatThrownBy(() -> repository.activate(91L, 12L))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));

    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), anyMap(), any(RowMapper.class));
    assertThat(sql.getValue()).contains("master_record_drafts", "FOR UPDATE");
  }

  @Test void historyRetentionDeletesRankedIdsBeyondTheRequestedNewestVersions() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(2);

    repository.retainLatestHistory(81L, 3);

    var sql = ArgumentCaptor.forClass(String.class);
    var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).update(sql.capture(), parameters.capture());
    assertThat(sql.getValue()).contains("DELETE FROM master_record_history", "ROW_NUMBER() OVER",
        "ORDER BY version DESC, id DESC", "ranked_position > :keep");
    assertThat(((MapSqlParameterSource) parameters.getValue()).getValue("record"))
        .isEqualTo(81L);
    assertThat(((MapSqlParameterSource) parameters.getValue()).getValue("keep"))
        .isEqualTo(3);
  }

  @Test void savingARejectedDraftAsAnUpdateCannotSilentlyOverwriteIt() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

    assertThatThrownBy(() -> repository.saveDraft(7L, 12L, draft(17L, RecordStatus.REJECTED)))
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.getMessage()).isEqualTo("Draft is no longer editable");
        });
  }

  @SuppressWarnings("unchecked")
  @Test void draftCollectionIsScopedToDepartmentActorAndRecoverableStatuses() {
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

    assertThat(repository.findDrafts(7L, 12L)).isEmpty();

    var sql = ArgumentCaptor.forClass(String.class);
    var parameters = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
    assertThat(sql.getValue()).contains("department_id=:department", "created_by=:actor",
        "'DRAFT','PENDING','REJECTED'");
    assertThat(parameters.getValue()).containsEntry("department", 7L)
        .containsEntry("actor", 12L);
  }

  private RecordDraft draft(long id, RecordStatus status) {
    return new RecordDraft(id, 81L, 9L, 7L, "CUS-20260805-0001", RecordAction.UPDATE,
        3L, Map.of("name", "North Supplier"), List.of(
            new RecordDraft.ChildRows(31L, List.of(
                new RecordDraft.ChildRow(101L, 0, Map.of("contact", "Li")),
                new RecordDraft.ChildRow(null, 1, Map.of("contact", "Wang"))))),
        status, 12L, null);
  }
}
