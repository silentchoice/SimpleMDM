package com.example.mdm.record;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.auth.AccountState;
import com.example.mdm.auth.AccountStateRepository;
import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.JwtService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.SecurityConfig;
import com.example.mdm.auth.TokenRevocationStore;
import com.example.mdm.auth.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@WebMvcTest(CodeRuleController.class)
@Import({SecurityConfig.class, JwtService.class, AuthorizationService.class, CodeRuleService.class,
    CodeRuleParser.class, CodeRuleControllerSecurityTest.FixedClockConfig.class,
    CodeRuleControllerSecurityTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class CodeRuleControllerSecurityTest {
  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockBean private AccountStateRepository accountStates;
  @MockBean private CodeSequenceRepository repository;

  @Test void onlySuperAdminCanReadOrChangeRuleAndPutReturnsFixedDatePreview() throws Exception {
    when(repository.findRule(41)).thenReturn(new CodeRule(41, "CUS-{yyyyMMdd}-{0001}", 4));
    String admin = token(1, Role.SUPER_ADMIN);
    mvc.perform(get("/api/master-type/41/code-rule").header("Authorization", admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.preview").value("CUS-20260805-0001"));
    mvc.perform(put("/api/master-type/41/code-rule").header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"CUS-{yyyyMMdd}-{0001}\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.preview").value("CUS-20260805-0001"));

    String editor = token(2, Role.DEPT_EDITOR);
    mvc.perform(get("/api/master-type/41/code-rule").header("Authorization", editor))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    mvc.perform(put("/api/master-type/41/code-rule").header("Authorization", editor)
            .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"CUS-{yyyyMMdd}-{0001}\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  }

  private String token(long id, Role role) {
    when(accountStates.findActive(id)).thenReturn(new AccountState(id, null, List.of(role)));
    return "Bearer " + jwt.issue(new UserPrincipal(id, "user" + id, "User " + id, null, List.of(role)));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfig {
    @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC); }
  }
  @TestConfiguration(proxyBeanMethods = false)
  static class RevocationConfig {
    @Bean TokenRevocationStore tokenRevocationStore() {
      return new TokenRevocationStore() {
        @Override public void revoke(String jti, java.time.Duration ttl) {}
        @Override public boolean isRevoked(String jti) { return false; }
      };
    }
  }
}
