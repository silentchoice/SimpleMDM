package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
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
    assertThat(sql.getValue()).contains("status='ACTIVE'");
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

  private FieldDefinition field(long id, String code) {
    return new FieldDefinition(id, 19L, code, code, FieldType.TEXT, false, List.of(), false, 0,
        MetadataStatus.ACTIVE);
  }
}
