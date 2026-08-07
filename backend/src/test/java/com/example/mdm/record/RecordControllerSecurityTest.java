package com.example.mdm.record;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RecordController.class)
@Import({SecurityConfig.class, JwtService.class, AuthorizationService.class,
    RecordControllerSecurityTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class RecordControllerSecurityTest {
  private static final DepartmentPrincipal SALES = new DepartmentPrincipal(7, "SALES", "Sales");
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @MockBean RecordQueryService queries;
  @MockBean RecordDraftService drafts;
  @MockBean AccountStateRepository accountStates;

  @Test void viewerCanReadFormalRecordsButCannotMutateOrReadDrafts() throws Exception {
    String token = token(13, Role.DEPT_VIEWER);
    when(queries.detail(81)).thenReturn(new RecordView(81, 9, 7, "CUS-1", Map.of(),
        List.of(), 1, "ACTIVE"));
    String command = "{\"recordId\":null,\"masterTypeId\":9,\"baseVersion\":0,"
        + "\"action\":\"CREATE\",\"masterValues\":{},\"children\":[]}";

    mvc.perform(get("/api/master-record/81").header("Authorization", token))
        .andExpect(status().isOk());
    mvc.perform(post("/api/master-record-draft").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(command))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    mvc.perform(put("/api/master-record-draft/91").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(command))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/master-record/81/delete-request").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/master-record-draft/91").header("Authorization", token))
        .andExpect(status().isForbidden());

    verify(queries).detail(81);
    verify(drafts, never()).create(Mockito.any());
  }

  @Test void anonymousFormalReadIsUnauthorized() throws Exception {
    mvc.perform(get("/api/master-record/81"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  }

  @Test void editorCanOpenTheCurrentUserDraftCollectionEndpoint() throws Exception {
    when(drafts.listMine()).thenReturn(List.of());
    mvc.perform(get("/api/master-record-draft")
            .header("Authorization", token(12, Role.DEPT_EDITOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
    mvc.perform(get("/api/master-record-draft")
            .header("Authorization", token(13, Role.DEPT_VIEWER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
    verify(drafts).listMine();
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
