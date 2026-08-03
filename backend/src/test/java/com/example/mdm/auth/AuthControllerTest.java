package com.example.mdm.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AuthenticationService authenticationService;

  @MockBean
  private JwtService jwtService;

  @MockBean
  private TokenRevocationStore tokenRevocationStore;

  @Test
  void validCredentialsReturnTokenUserRolesDepartmentAndRequestId() throws Exception {
    var principal = new UserPrincipal(7L, "alice", "Alice", new DepartmentPrincipal(3L, "SALES", "Sales"),
        java.util.List.of(Role.DEPT_EDITOR));
    when(authenticationService.authenticate("alice", "correct-password")).thenReturn(principal);
    when(jwtService.issue(principal)).thenReturn("signed.jwt.token");

    mockMvc.perform(post("/api/auth/login")
            .header("X-Request-Id", "req-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"alice\",\"password\":\"correct-password\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "req-login"))
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.message").value("OK"))
        .andExpect(jsonPath("$.data.accessToken").value("signed.jwt.token"))
        .andExpect(jsonPath("$.data.user.username").value("alice"))
        .andExpect(jsonPath("$.data.roles[0]").value("DEPT_EDITOR"))
        .andExpect(jsonPath("$.data.department.code").value("SALES"))
        .andExpect(jsonPath("$.requestId").value("req-login"));
  }

  @Test
  void invalidCredentialsReturnTheSameUnauthorizedResponseWithoutAccountDisclosure() throws Exception {
    when(authenticationService.authenticate(any(), any()))
        .thenThrow(BusinessException.unauthorized());

    mockMvc.perform(post("/api/auth/login")
            .header("X-Request-Id", "req-invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"alice\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value("Invalid username or password"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").value("req-invalid"));
  }

  @Test
  void malformedLoginJsonReturnsBadRequestInTheSharedResponseContract() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .header("X-Request-Id", "req-malformed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("Invalid request"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").value("req-malformed"));
  }

  @Test
  void menuWithoutBearerTokenReturnsUnauthorizedResponseWithRequestId() throws Exception {
    mockMvc.perform(get("/api/auth/menu"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value("Unauthorized"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }
}
