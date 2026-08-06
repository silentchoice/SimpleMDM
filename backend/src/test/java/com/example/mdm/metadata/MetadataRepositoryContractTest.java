package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class MetadataRepositoryContractTest {
  @Test
  void readsOnlyTheCurrentActiveAssignmentForTheRequestedDepartment() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var assigned = new MasterType(19L, "ASSET", "Asset", MetadataStatus.ACTIVE);
    when(jdbc.<MasterType>query(contains("department_master_types"), anyMap(),
        org.mockito.ArgumentMatchers.<RowMapper<MasterType>>any())).thenReturn(List.of(assigned));
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());

    assertThat(repository.findAssignedMasterType(7L)).isEqualTo(assigned);

    var sql = ArgumentCaptor.forClass(String.class);
    var parameters = ArgumentCaptor.forClass(Map.class);
    org.mockito.Mockito.verify(jdbc).query(sql.capture(), parameters.capture(),
        org.mockito.ArgumentMatchers.<RowMapper<MasterType>>any());
    assertThat(sql.getValue()).contains("department_master_types").contains("dmt.status='ACTIVE'")
        .contains("mt.status='ACTIVE'");
    assertThat(parameters.getValue()).containsEntry("department", 7L);
  }
  @Test
  void mapsSecondActiveAssignmentForDepartmentToConflict() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    Set<Long> activeDepartmentIds = new HashSet<>();
    when(jdbc.update(contains("INSERT INTO department_master_types"), anyMap())).thenAnswer(invocation -> {
      Map<String, Object> parameters = invocation.getArgument(1);
      if (!activeDepartmentIds.add((Long) parameters.get("department"))) {
        throw new DuplicateKeyException("duplicate active assignment");
      }
      return 1;
    });
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());

    repository.assignDepartment(7L, 19L);

    assertThatThrownBy(() -> repository.assignDepartment(7L, 20L))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status(), Throwable::getMessage)
        .containsExactly(HttpStatus.CONFLICT, "Metadata conflict");
  }

  @Test
  void readsOnlyActiveSubTypesWithinDepartment() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    when(jdbc.<SubType>query(contains("FROM sub_types"), anyMap(),
        org.mockito.ArgumentMatchers.<RowMapper<SubType>>any())).thenReturn(List.of());
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());

    repository.findSubTypes(7L, 19L);

    var sql = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbc).query(sql.capture(), anyMap(),
        org.mockito.ArgumentMatchers.<RowMapper<SubType>>any());
    assertThat(sql.getValue()).contains("status='ACTIVE'")
        .contains("ORDER BY sort_order,id");
  }

  @Test
  void returnsSeparateMasterFieldListsWhenDepartmentsUseOneTemplate() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var departmentOneFields = List.of(field(101L, "employeeNumber"));
    var departmentTwoFields = List.of(field(202L, "vendorNumber"));
    when(jdbc.<FieldDefinition>query(contains("FROM master_fields"), anyMap(),
        org.mockito.ArgumentMatchers.<RowMapper<FieldDefinition>>any()))
        .thenAnswer(invocation -> {
          Map<String, Object> parameters = invocation.getArgument(1);
          return parameters.get("department").equals(7L) ? departmentOneFields : departmentTwoFields;
        });
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());

    assertThat(repository.findMasterFields(7L, 19L)).containsExactlyElementsOf(departmentOneFields);
    assertThat(repository.findMasterFields(8L, 19L)).containsExactlyElementsOf(departmentTwoFields);
  }

  @Test
  void bindsDepartmentWhenCreatingMasterFields() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(contains("FROM department_master_types"), anyMap(),
        org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(1);
    when(jdbc.update(contains("INSERT INTO master_fields"), any(MapSqlParameterSource.class),
        any(GeneratedKeyHolder.class))).thenThrow(new DataIntegrityViolationException("invalid metadata"));
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());

    assertThatThrownBy(() -> repository.createMasterField(7L, field(101L, "employeeNumber")))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status(), Throwable::getMessage)
        .containsExactly(HttpStatus.CONFLICT, "Metadata conflict");

    var parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    org.mockito.Mockito.verify(jdbc).update(contains("INSERT INTO master_fields"), parameters.capture(),
        any(GeneratedKeyHolder.class));
    assertThat(parameters.getValue().getValue("department")).isEqualTo(7L);
  }

  @Test
  void bindsAndSelectsMasterFieldSharingConfiguration() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(contains("FROM department_master_types"), anyMap(),
        org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(1);
    when(jdbc.update(contains("INSERT INTO master_fields"), any(MapSqlParameterSource.class),
        any(GeneratedKeyHolder.class))).thenThrow(new DataIntegrityViolationException("stop"));
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());
    var shared = new FieldDefinition(0, 19, "publicName", "Public name", FieldType.TEXT,
        false, List.of(), true, 0, MetadataStatus.ACTIVE);

    assertThatThrownBy(() -> repository.createMasterField(7, shared))
        .isInstanceOf(BusinessException.class);
    repository.findMasterFields(7, 19);

    var parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO master_fields"), parameters.capture(),
        any(GeneratedKeyHolder.class));
    assertThat(parameters.getValue().getValue("shared")).isEqualTo(true);
    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), anyMap(), any(RowMapper.class));
    assertThat(sql.getValue()).contains("share_config", "FROM master_fields");
  }

  @Test void subtypeReplacementUpdatesRetainedIdentityAndInsertsOnlyNewCodes() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());
    when(jdbc.<SubType>query(contains("FROM sub_types"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of(new SubType(55, 19, "retained", "Old", MetadataStatus.ACTIVE)));
    when(jdbc.update(contains("UPDATE sub_types"), anyMap())).thenReturn(1);
    when(jdbc.update(contains("INSERT INTO sub_types"), any(MapSqlParameterSource.class),
        any(GeneratedKeyHolder.class))).thenReturn(1);

    repository.replaceSubTypes(7, 19, List.of(
        new SubType(55, 19, "retained", "New", MetadataStatus.ACTIVE),
        new SubType(0, 19, "added", "Added", MetadataStatus.ACTIVE)));

    var updateSql = ArgumentCaptor.forClass(String.class);
    var updateParameters = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(updateSql.capture(), updateParameters.capture());
    assertThat(updateSql.getValue()).contains("sort_order=:position");
    assertThat(updateParameters.getValue()).containsEntry("id", 55L).containsEntry("position", 0);
    var insertParameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO sub_types"), insertParameters.capture(),
        any(GeneratedKeyHolder.class));
    assertThat(insertParameters.getValue().getValue("position")).isEqualTo(1);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
        .update(contains("DELETE FROM sub_types"), anyMap());
  }

  @Test void retainedSubtypeIdsCanSwapApprovedPositionsWithoutDeleteOrUniqueConflict() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());
    when(jdbc.<SubType>query(contains("FROM sub_types"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of(new SubType(55, 19, "first", "First", MetadataStatus.ACTIVE),
            new SubType(56, 19, "second", "Second", MetadataStatus.ACTIVE)));
    when(jdbc.update(contains("UPDATE sub_types"), anyMap())).thenReturn(1);

    repository.replaceSubTypes(7, 19, List.of(
        new SubType(56, 19, "second", "Second", MetadataStatus.ACTIVE),
        new SubType(55, 19, "first", "First", MetadataStatus.ACTIVE)));

    var parameters = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.times(2)).update(contains("UPDATE sub_types"), parameters.capture());
    assertThat(parameters.getAllValues()).extracting(values -> values.get("id"), values -> values.get("position"))
        .containsExactly(org.assertj.core.groups.Tuple.tuple(56L, 0),
            org.assertj.core.groups.Tuple.tuple(55L, 1));
    assertThat(parameters.getAllValues()).allSatisfy(values -> {
      assertThat(values).containsEntry("department", 7L).containsEntry("owner", 19L);
    });
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
        .update(contains("DELETE FROM sub_types"), anyMap());
  }

  @Test void removingReferencedSubtypeReturnsStableConflict() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());
    when(jdbc.<SubType>query(contains("FROM sub_types"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of(new SubType(55, 19, "removed", "Removed", MetadataStatus.ACTIVE)));
    when(jdbc.queryForObject(contains("approval_tasks"), anyMap(),
        org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(1);

    assertThatThrownBy(() -> repository.replaceSubTypes(7, 19, List.of(
        new SubType(0, 19, "new", "New", MetadataStatus.ACTIVE))))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
        .update(contains("DELETE FROM sub_types"), anyMap());
  }

  @Test void subtypeDeleteConstraintFailureReturnsStableConflict() {
    var jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var repository = new JdbcMetadataRepository(jdbc, new ObjectMapper());
    when(jdbc.<SubType>query(contains("FROM sub_types"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of(new SubType(55, 19, "removed", "Removed", MetadataStatus.ACTIVE)));
    when(jdbc.queryForObject(contains("approval_tasks"), anyMap(),
        org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(0);
    when(jdbc.update(contains("DELETE FROM sub_types"), anyMap()))
        .thenThrow(new DataIntegrityViolationException("referenced"));

    assertThatThrownBy(() -> repository.replaceSubTypes(7, 19, List.of(
        new SubType(0, 19, "new", "New", MetadataStatus.ACTIVE))))
        .isInstanceOfSatisfying(BusinessException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.getMessage()).isEqualTo("Metadata conflict");
        });
  }

  private FieldDefinition field(long id, String code) {
    return new FieldDefinition(id, 19L, code, code, FieldType.TEXT, false, List.of(), false, 0,
        MetadataStatus.ACTIVE);
  }
}
