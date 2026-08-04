package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class MetadataAssignmentLockContractTest {
  @SuppressWarnings("unchecked")
  @Test void lockUsesForUpdateAndDistinguishesForeignTemplateFromMissingTemplate() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      assertThat(sql).contains("FOR UPDATE");
      return List.of();
    });
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1, 0);

    assertThatThrownBy(() -> repository.lockTemplateAssignment(7, 88))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> repository.lockTemplateAssignment(7, 404))
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
