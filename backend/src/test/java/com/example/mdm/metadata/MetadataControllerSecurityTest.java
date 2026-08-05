package com.example.mdm.metadata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.auth.AccountState;
import com.example.mdm.auth.AccountStateRepository;
import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.JwtService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.SecurityConfig;
import com.example.mdm.auth.TokenRevocationStore;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    MasterTypeController.class, MasterFieldController.class,
    SubTypeController.class, SubFieldController.class
})
@Import({SecurityConfig.class, JwtService.class, AuthorizationService.class, MetadataService.class,
    FieldStructureValidator.class, MetadataControllerSecurityTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class MetadataControllerSecurityTest {
  private static final DepartmentPrincipal SALES =
      new DepartmentPrincipal(7, "SALES", "Sales");

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockBean private AccountStateRepository accountStates;
  @MockBean private MetadataRepository metadata;
  @MockBean private MetadataApprovalRepository approvals;

  @Test
  void onlySuperAdminCanCreateAndAssignMasterTypes() throws Exception {
    when(metadata.createMasterType("ASSET", "Asset", 1))
        .thenReturn(new MasterType(41, "ASSET", "Asset", MetadataStatus.ACTIVE));
    String admin = token(1, null, Role.SUPER_ADMIN);

    mvc.perform(post("/api/master-type").header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"asset\",\"name\":\"Asset\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(41));
    mvc.perform(put("/api/master-type/41/departments/7").header("Authorization", admin))
        .andExpect(status().isOk());
    verify(metadata).assignDepartment(7, 41);

    String editor = token(2, SALES, Role.DEPT_EDITOR);
    mvc.perform(post("/api/master-type").header("Authorization", editor)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"other\",\"name\":\"Other\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    mvc.perform(put("/api/master-type/41/departments/7").header("Authorization", editor))
        .andExpect(status().isForbidden());
  }

  @Test
  void editorSubmitsButViewerApproverAndAnonymousCannot() throws Exception {
    when(metadata.findMasterFields(7, 41)).thenReturn(List.of());
    when(approvals.submit(any())).thenReturn(701L);
    String body = "[{\"code\":\"serial\",\"displayName\":\"Serial\","
        + "\"fieldType\":\"TEXT\",\"options\":[],\"sortOrder\":0}]";

    mvc.perform(post("/api/master-field/41")
            .header("Authorization", token(2, SALES, Role.DEPT_EDITOR))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalTaskId").value(701));
    verify(metadata).lockTemplateAssignment(7, 41);

    for (Role role : List.of(Role.DEPT_VIEWER, Role.DEPT_APPROVER)) {
      mvc.perform(post("/api/master-field/41")
              .header("Authorization", token(role == Role.DEPT_VIEWER ? 3 : 4, SALES, role))
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    mvc.perform(post("/api/master-field/41")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  }

  @Test
  void viewerReadsAuthenticatedDepartmentActiveDefinitionsWithRequestIdConvention()
      throws Exception {
    when(metadata.findMasterFields(7, 41)).thenReturn(List.of(
        new FieldDefinition(11, 41, "serial", "Serial", FieldType.TEXT, false, List.of(),
            false, 0, MetadataStatus.ACTIVE)));

    mvc.perform(get("/api/master-field/41")
            .header("Authorization", token(3, SALES, Role.DEPT_VIEWER))
            .header("X-Request-Id", "req-metadata-read"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "req-metadata-read"))
        .andExpect(jsonPath("$.requestId").value("req-metadata-read"))
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    verify(metadata).requireTemplateAccess(7, 41);
    verify(metadata).findMasterFields(7, 41);
    verify(metadata, never()).findMasterFields(41);
  }

  @Test
  void onlyDepartmentMetadataRolesReadTheirCurrentAssignment() throws Exception {
    when(metadata.findAssignedMasterType(7L))
        .thenReturn(new MasterType(41L, "ASSET", "Asset", MetadataStatus.ACTIVE));
    for (Role role : List.of(Role.DEPT_EDITOR, Role.DEPT_APPROVER, Role.DEPT_VIEWER)) {
      mvc.perform(get("/api/master-type/current")
              .header("Authorization", token(role.ordinal() + 20L, SALES, role)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(41));
    }
    mvc.perform(get("/api/master-type/current").header("Authorization", token(1L, null, Role.SUPER_ADMIN)))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    mvc.perform(get("/api/master-type/current"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  }

  @Test
  void crossDepartmentTemplateIdIsForbiddenThroughRealServiceBoundary() throws Exception {
    doThrow(BusinessException.forbidden()).when(metadata).requireTemplateAccess(7, 88);

    mvc.perform(get("/api/master-field/88")
            .header("Authorization", token(3, SALES, Role.DEPT_VIEWER)))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    verify(metadata, never()).findMasterFields(7, 88);
  }

  @Test
  void subTypePathControlsMasterTypeIdInsteadOfRequestBody() throws Exception {
    when(metadata.findSubTypes(7, 41)).thenReturn(List.of());
    when(approvals.submit(any())).thenReturn(702L);

    mvc.perform(post("/api/sub-type/41")
            .header("Authorization", token(2, SALES, Role.DEPT_EDITOR))
            .contentType(MediaType.APPLICATION_JSON)
            .content("[{\"masterTypeId\":999,\"code\":\"device\",\"name\":\"Device\"}]"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalTaskId").value(702));
    verify(metadata).lockTemplateAssignment(7, 41);
    verify(metadata, never()).lockTemplateAssignment(7, 999);
  }

  @Test
  void validJsonMissingFieldTypeIsBadRequestBeforeSubmission() throws Exception {
    mvc.perform(post("/api/master-field/41")
            .header("Authorization", token(2, SALES, Role.DEPT_EDITOR))
            .contentType(MediaType.APPLICATION_JSON)
            .content("[{\"code\":\"serial\",\"displayName\":\"Serial\"}]"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    verify(approvals, never()).submit(any());
  }

  private String token(long id, DepartmentPrincipal department, Role role) {
    when(accountStates.findActive(id))
        .thenReturn(new AccountState(id, department == null ? null : department.id(), List.of(role)));
    var principal = new UserPrincipal(id, "user" + id, "User " + id, department, List.of(role));
    return "Bearer " + jwt.issue(principal);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RevocationConfig {
    @Bean
    TokenRevocationStore tokenRevocationStore() {
      return new TokenRevocationStore() {
        @Override public void revoke(String jti, Duration ttl) {}
        @Override public boolean isRevoked(String jti) { return false; }
      };
    }
  }
}
