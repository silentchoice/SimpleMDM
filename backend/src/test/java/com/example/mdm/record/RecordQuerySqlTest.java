package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.metadata.MetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RecordQuerySqlTest {
  @SuppressWarnings("unchecked")
  @Test void jdbcListPushesFiltersVisibilitySafeKeywordCountSortAndPageIntoSql() {
    var jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
    var authorization = Mockito.mock(AuthorizationService.class);
    when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
        Role.DEPT_VIEWER)).thenReturn(new UserPrincipal(12, "viewer", "Viewer",
            new DepartmentPrincipal(7, "D7", "Department 7"), List.of(Role.DEPT_VIEWER)));
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(2L);
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
    var json = new ObjectMapper();
    var service = new RecordQueryService(jdbc, json, new RecordSnapshotCodec(json),
        new RecordVisibilityService(Mockito.mock(MetadataRepository.class)), authorization);

    var page = service.list(new RecordQueryService.RecordQuery(9L, "CUS", "shared-value",
        "ACTIVE", true, 1, 10, "recordCode", "asc",
        LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59)));

    assertThat(page.totalElements()).isEqualTo(2);
    var countSql = ArgumentCaptor.forClass(String.class);
    var countParameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).queryForObject(countSql.capture(), countParameters.capture(), eq(Long.class));
    assertThat(countSql.getValue()).contains("COUNT(*)", "record.master_type_id=:masterTypeId",
        "record.status=:status", "record.updated_at>=:updatedFrom",
        "record.updated_at<=:updatedTo", "master_fields", "sub_records", "share_config");
    assertThat(countParameters.getValue().getValue("viewerDepartment")).isEqualTo(7L);

    var pageSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(pageSql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    assertThat(pageSql.getValue()).contains("ORDER BY LOWER(record.record_code) ASC,record.id ASC",
        "LIMIT :limit OFFSET :offset");
  }
}
