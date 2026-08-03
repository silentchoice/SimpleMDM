package com.example.mdm.auth;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminOnlyController.class)
@Import({SecurityConfig.class, JwtService.class, RoleProtectedEndpointTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class RoleProtectedEndpointTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @Test
  void userWithoutRequiredRoleReceivesForbiddenSharedResponse() throws Exception {
    String token = jwtService.issue(new UserPrincipal(7L, "viewer", "Viewer", null, List.of(Role.DEPT_VIEWER)));

    mockMvc.perform(get("/api/test/admin").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(jsonPath("$.message").value("Forbidden"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RevocationConfig {
    @Bean
    TokenRevocationStore tokenRevocationStore() {
      return new TokenRevocationStore() {
        @Override
        public void revoke(String jti, Duration ttl) {
        }

        @Override
        public boolean isRevoked(String jti) {
          return false;
        }
      };
    }
  }
}
