package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.mdm.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

class JdbcMetadataApprovalRepositoryTest {
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
}
