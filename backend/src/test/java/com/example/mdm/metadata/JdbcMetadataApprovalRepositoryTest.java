package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.example.mdm.common.error.BusinessException;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

class JdbcMetadataApprovalRepositoryTest {
  @SuppressWarnings("unchecked")
  @Test void listBindsDepartmentStatusAndMetadataKindsAndMapsCompleteAuditProjection() throws Exception {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    var result = Mockito.mock(ResultSet.class);
    LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 4, 9, 30);
    LocalDateTime reviewedAt = LocalDateTime.of(2026, 8, 4, 10, 15);
    when(result.getLong("id")).thenReturn(91L);
    when(result.getString("entity_type")).thenReturn("MASTER_FIELDS");
    when(result.getLong("entity_id")).thenReturn(41L);
    when(result.getString("status")).thenReturn("APPROVED");
    when(result.getString("before_snapshot")).thenReturn("{\"before\":true}");
    when(result.getString("after_snapshot")).thenReturn("{\"after\":true}");
    when(result.getLong("submitted_by")).thenReturn(12L);
    when(result.getObject("reviewed_by", Long.class)).thenReturn(23L);
    when(result.getString("review_comment")).thenReturn("looks good");
    when(result.getObject("submitted_at", LocalDateTime.class)).thenReturn(submittedAt);
    when(result.getObject("reviewed_at", LocalDateTime.class)).thenReturn(reviewedAt);
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
      RowMapper<MetadataApprovalRepository.ApprovalTaskView> mapper = invocation.getArgument(2);
      return List.of(mapper.mapRow(result, 0));
    });

    assertThat(repository.list(7, "APPROVED")).containsExactly(
        new MetadataApprovalRepository.ApprovalTaskView(91, "MASTER_FIELDS", 41, "APPROVED",
            "{\"before\":true}", "{\"after\":true}", 12, 23L, "looks good",
            submittedAt, reviewedAt));

    var sql = ArgumentCaptor.forClass(String.class);
    var parameters = ArgumentCaptor.forClass(java.util.Map.class);
    verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
    assertThat(sql.getValue()).contains("department_id=:department", "status=:status",
        "entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')");
    assertThat(parameters.getValue()).containsEntry("department", 7L)
        .containsEntry("status", "APPROVED");
  }

  @SuppressWarnings("unchecked")
  @Test void detailBindsDepartmentAndDistinguishesForeignFromAbsentWithoutReadingForeignSnapshots() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1, 0);

    assertThatThrownBy(() -> repository.detail(7, 91))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> repository.detail(7, 404))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));

    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, Mockito.times(2)).query(sql.capture(), anyMap(), any(RowMapper.class));
    assertThat(sql.getAllValues()).allSatisfy(value ->
        assertThat(value).contains("department_id=:department", "id=:id",
            "entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')"));
    var existenceSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, Mockito.times(2)).queryForObject(existenceSql.capture(), anyMap(), eq(Integer.class));
    assertThat(existenceSql.getAllValues()).allSatisfy(value -> {
      assertThat(value).contains("COUNT(*)", "id=:id",
          "entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')");
      assertThat(value).doesNotContain("snapshot");
    });
  }

  @SuppressWarnings("unchecked")
  @Test void nonMetadataTaskIsAbsentForDetailAndActionAndIsNeverUpdated() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);

    assertThatThrownBy(() -> repository.detail(7, 91))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> repository.lock(7, 91))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));

    var existenceSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, Mockito.times(2)).queryForObject(existenceSql.capture(), anyMap(), eq(Integer.class));
    assertThat(existenceSql.getAllValues()).allSatisfy(value ->
        assertThat(value).contains("entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')"));
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test void duplicatePendingTaskMapsToStableConflict() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    when(jdbc.update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class)))
        .thenThrow(new DuplicateKeyException("uk_approval_tasks_pending_metadata"));
    var request = new MetadataChangeRequest(7, 12, "MASTER_FIELDS", 41, "{}", "{}");

    assertThatThrownBy(() -> repository.submit(request))
        .isInstanceOfSatisfying(BusinessException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.getMessage()).isEqualTo("Pending metadata approval already exists");
        });
  }

  @SuppressWarnings("unchecked")
  @Test void foreignSubtypeIsForbiddenWhileUnknownSubtypeIsNotFound() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1, 0);

    assertThatThrownBy(() -> repository.requireSubTypeTemplate(7, 55))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> repository.requireSubTypeTemplate(7, 404))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @SuppressWarnings("unchecked")
  @Test void lockedTaskIsDepartmentScopedAndDistinguishesForeignFromAbsent() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1, 0);

    assertThatThrownBy(() -> repository.lock(7, 91))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> repository.lock(7, 404))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, Mockito.times(2)).query(sql.capture(), anyMap(), any(RowMapper.class));
    assertThat(sql.getAllValues()).allSatisfy(value ->
        assertThat(value).contains("department_id=:department", "id=:id", "FOR UPDATE",
            "entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')"));
    var existenceSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, Mockito.times(2)).queryForObject(existenceSql.capture(), anyMap(), eq(Integer.class));
    assertThat(existenceSql.getAllValues()).allSatisfy(value ->
        assertThat(value).contains("COUNT(*)",
            "entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')"));
  }

  @Test void lostPendingTransitionIsStableConflictAndUpdateIsGuarded() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataApprovalRepository(jdbc);
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

    assertThatThrownBy(() -> repository.reject(7, 9, 23, "reason"))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(SqlParameterSource.class));
    assertThat(sql.getValue()).contains(
        "WHERE department_id=:department AND id=:id AND status='PENDING'",
        "entity_type IN ('MASTER_FIELDS','SUB_TYPES','SUB_FIELDS')");
  }
}
