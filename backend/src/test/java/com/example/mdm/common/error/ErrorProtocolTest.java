package com.example.mdm.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.auth.AuthController;
import com.example.mdm.auth.AuthenticationService;
import com.example.mdm.auth.JwtService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.TokenRevocationStore;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.auth.SecurityConfig;
import java.time.Duration;
import java.util.List;
import com.example.mdm.auth.TokenRevocationStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, ErrorProtocolTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class ErrorProtocolTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @MockBean
  private AuthenticationService authenticationService;

  @Test
  void unknownRouteReturnsNotFoundInTheSharedResponseContract() throws Exception {
    mockMvc.perform(get("/unknown-resource")
            .header("Authorization", "Bearer " + jwtService.issue(principal()))
            .header("X-Request-Id", "req-404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not found"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").value("req-404"));
  }

  @Test
  void unsupportedMethodReturnsMethodNotAllowedInTheSharedResponseContract() throws Exception {
    mockMvc.perform(get("/api/auth/login").header("X-Request-Id", "req-405"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value(405))
        .andExpect(jsonPath("$.message").value("Method not allowed"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").value("req-405"));
  }

  @Test
  void unsupportedMediaTypeReturnsUnsupportedMediaTypeInTheSharedResponseContract() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .header("X-Request-Id", "req-415")
            .contentType(MediaType.TEXT_PLAIN)
            .content("not-json"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value(415))
        .andExpect(jsonPath("$.message").value("Unsupported media type"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").value("req-415"));
  }

  private UserPrincipal principal() {
    return new UserPrincipal(7L, "viewer", "Viewer", null, List.of(Role.DEPT_VIEWER));
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
