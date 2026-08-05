package com.example.mdm.metadata;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.LocalDateTime;
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

@WebMvcTest(controllers = MetadataApprovalController.class)
@Import({SecurityConfig.class, JwtService.class, AuthorizationService.class,
    MetadataApprovalQueryService.class, MetadataApprovalApplicationService.class,
    FieldStructureValidator.class, MetadataApprovalControllerSecurityTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class MetadataApprovalControllerSecurityTest {
  private static final DepartmentPrincipal SALES = new DepartmentPrincipal(7, "SALES", "Sales");

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockBean private AccountStateRepository accountStates;
  @MockBean private MetadataApprovalRepository approvals;
  @MockBean private MetadataRepository metadata;

  @Test void approverListsItsDepartmentAndRejectsAsAuthenticatedReviewer() throws Exception {
    when(approvals.list(7, "PENDING")).thenReturn(List.of(view(91)));
    when(approvals.lock(7, 91)).thenReturn(
        new MetadataApprovalRepository.ApprovalTask(91, 7, "MASTER_FIELDS", 41, "{}", "{}", "PENDING"));
    String approver = token(23, SALES, Role.DEPT_APPROVER);

    mvc.perform(get("/api/metadata-approval").header("Authorization", approver)
            .header("X-Request-Id", "req-secure-list"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(91))
        .andExpect(jsonPath("$.requestId").value("req-secure-list"));
    mvc.perform(post("/api/metadata-approval/91/reject").header("Authorization", approver)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"needs work\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

    verify(approvals).list(7, "PENDING");
    verify(approvals).reject(7, 91, 23, "needs work");
  }

  @Test void editorViewerAndAnonymousCannotUseApprovalRoutes() throws Exception {
    for (Role role : List.of(Role.DEPT_EDITOR, Role.DEPT_VIEWER)) {
      String denied = token(role == Role.DEPT_EDITOR ? 12 : 13, SALES, role);
      mvc.perform(get("/api/metadata-approval").header("Authorization", denied))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
      mvc.perform(post("/api/metadata-approval/91/reject").header("Authorization", denied)
              .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"denied\"}"))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    mvc.perform(get("/api/metadata-approval"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    verify(approvals, never()).list(7, "PENDING");
    verify(approvals, never()).lock(7, 91);
  }

  @Test void crossDepartmentDetailIsForbiddenThroughRealQueryServiceBoundary() throws Exception {
    when(approvals.detail(7, 91)).thenThrow(BusinessException.forbidden());

    mvc.perform(get("/api/metadata-approval/91")
            .header("Authorization", token(23, SALES, Role.DEPT_APPROVER)))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    verify(approvals).detail(7, 91);
  }

  private String token(long id, DepartmentPrincipal department, Role role) {
    when(accountStates.findActive(id))
        .thenReturn(new AccountState(id, department.id(), List.of(role)));
    return "Bearer " + jwt.issue(new UserPrincipal(id, "user" + id, "User " + id,
        department, List.of(role)));
  }

  private MetadataApprovalRepository.ApprovalTaskView view(long id) {
    return new MetadataApprovalRepository.ApprovalTaskView(id, "MASTER_FIELDS", 41, "PENDING",
        "{}", "{}", 12, null, null, LocalDateTime.of(2026, 8, 4, 9, 30), null);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RevocationConfig {
    @Bean TokenRevocationStore tokenRevocationStore() {
      return new TokenRevocationStore() {
        @Override public void revoke(String jti, Duration ttl) {}
        @Override public boolean isRevoked(String jti) { return false; }
      };
    }
  }
}
