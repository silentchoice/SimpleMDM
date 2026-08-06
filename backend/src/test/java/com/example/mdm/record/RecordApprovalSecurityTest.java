package com.example.mdm.record;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

@WebMvcTest(controllers = RecordApprovalController.class)
@Import({SecurityConfig.class, JwtService.class, AuthorizationService.class,
    RecordApprovalSecurityTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=0123456789abcdef0123456789abcdef",
    "app.jwt.access-token-ttl=PT15M"
})
class RecordApprovalSecurityTest {
  private static final DepartmentPrincipal SALES = new DepartmentPrincipal(7, "SALES", "Sales");
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @MockBean RecordApprovalService service;
  @MockBean AccountStateRepository accountStates;

  @Test void editorCanSubmitButCannotReview() throws Exception {
    String token = token(12, Role.DEPT_EDITOR);
    when(service.submit(91, "held-token")).thenReturn(701L);

    mvc.perform(post("/api/master-record-draft/91/submit").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"held-token\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/record-approval/701/approve").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":null}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    verify(service).submit(91, "held-token");
    verify(service, never()).approve(701, null);
  }

  @Test void approverCanReviewButCannotSubmitAndAnonymousIsUnauthorized() throws Exception {
    String token = token(23, Role.DEPT_APPROVER);

    mvc.perform(post("/api/record-approval/701/reject").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"incorrect\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/master-record-draft/91/submit").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":null}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/record-approval/701/approve")
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":null}"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    verify(service).reject(701, "incorrect");
    verify(service, never()).submit(91, null);
  }

  private String token(long id, Role role) {
    when(accountStates.findActive(id)).thenReturn(new AccountState(id, SALES.id(), List.of(role)));
    return "Bearer " + jwt.issue(new UserPrincipal(id, "user" + id, "User " + id,
        SALES, List.of(role)));
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
