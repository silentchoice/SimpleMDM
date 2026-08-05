package com.example.mdm.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DepartmentMetadataIsolationTest {
  @Test void replacementSqlAlwaysScopesDeleteAndInsertToOneDepartment() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/example/mdm/metadata/JdbcMetadataRepository.java"),
        StandardCharsets.UTF_8);
    assertThat(source).contains("DELETE FROM master_fields WHERE department_id=:department")
        .contains("DELETE FROM sub_types WHERE department_id=:department")
        .contains("DELETE FROM sub_fields WHERE department_id=:department")
        .contains("Map.of(\"department\",departmentId");
  }

  @Test void approvingDepartmentALeavesSharedTemplateDepartmentBByteForByteUnchanged() throws Exception {
    var approvals = Mockito.mock(MetadataApprovalRepository.class);
    var metadata = Mockito.mock(MetadataRepository.class);
    var authorization = Mockito.mock(AuthorizationService.class);
    var validator = Mockito.mock(FieldStructureValidator.class);
    var json = new ObjectMapper();
    var active = new HashMap<Long, List<FieldDefinition>>();
    var oldA = List.of(field(1, "a_old"));
    var newA = List.of(field(0, "a_new"));
    var originalB = List.of(field(2, "b_untouched"));
    active.put(7L, oldA);
    active.put(8L, originalB);
    byte[] bBefore = json.writeValueAsBytes(active.get(8L));
    String base = fingerprint(json, oldA);
    String before = envelope(json, 7, 41, base, oldA);
    String after = envelope(json, 7, 41, base, newA);
    when(authorization.requireRole(Role.DEPT_APPROVER)).thenReturn(new UserPrincipal(23, "approver",
        "Approver", new DepartmentPrincipal(7, "D7", "Department 7"), List.of(Role.DEPT_APPROVER)));
    when(approvals.lock(7, 9)).thenReturn(new MetadataApprovalRepository.ApprovalTask(
        9, 7, "MASTER_FIELDS", 41, before, after, "PENDING"));
    when(metadata.findMasterFields(Mockito.anyLong(), Mockito.eq(41L)))
        .thenAnswer(invocation -> active.get(invocation.getArgument(0, Long.class)));
    doAnswer(invocation -> {
      active.put(invocation.getArgument(0, Long.class), List.copyOf(invocation.getArgument(2)));
      return null;
    }).when(metadata).replaceMasterFields(Mockito.eq(7L), Mockito.eq(41L), Mockito.anyList());

    new MetadataApprovalApplicationService(approvals, metadata, authorization, validator, json)
        .approve(9, null);

    assertThat(active.get(7L)).containsExactlyElementsOf(newA);
    assertThat(json.writeValueAsBytes(active.get(8L))).containsExactly(bBefore);
  }

  private FieldDefinition field(long id, String code) {
    return new FieldDefinition(id, 41, code, code, FieldType.TEXT, false, List.of(), false, 1,
        MetadataStatus.ACTIVE);
  }

  private String fingerprint(ObjectMapper json, List<FieldDefinition> fields) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(json.writeValueAsBytes(fields)));
  }

  private String envelope(ObjectMapper json, long department, long template, String fingerprint,
      List<FieldDefinition> fields) throws Exception {
    return json.writeValueAsString(Map.of("schemaVersion", 1, "departmentId", department,
        "templateId", template, "entityKind", "MASTER_FIELDS", "baseFingerprint", fingerprint,
        "orderedDefinitions", fields));
  }
}
