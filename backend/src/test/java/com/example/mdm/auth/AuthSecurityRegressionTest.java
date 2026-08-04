package com.example.mdm.auth;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, AuthSecurityRegressionTest.RevocationConfig.class})
@TestPropertySource(properties = {
    "app.jwt.secret=01234567890123456789012345678901",
    "app.jwt.expiration-seconds=60"
})
class AuthSecurityRegressionTest {
  private static final String SECRET = "01234567890123456789012345678901";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private InMemoryTokenRevocationStore tokenRevocationStore;

  @MockBean
  private AuthenticationService authenticationService;

  @MockBean
  private AccountStateRepository accountStateRepository;

  @BeforeEach
  void resetRevocationStore() {
    tokenRevocationStore.failReads = false;
    org.mockito.Mockito.when(accountStateRepository.findActive(7L))
        .thenReturn(new AccountState(7L, null, List.of(Role.DEPT_VIEWER)));
  }

  @Test
  void tamperedTokenReturnsUnauthorizedSharedResponse() throws Exception {
    String token = jwtService.issue(principal());
    String tampered = token.substring(0, token.length() - 1) + "x";

    mockMvc.perform(get("/api/auth/menu").header("Authorization", "Bearer " + tampered))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value("Unauthorized"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void expiredTokenReturnsUnauthorizedSharedResponse() throws Exception {
    String expired = Jwts.builder()
        .id("expired-jti")
        .subject("alice")
        .claim("userId", 7L)
        .claim("displayName", "Alice")
        .claim("roles", List.of("DEPT_VIEWER"))
        .issuedAt(java.util.Date.from(Instant.now().minusSeconds(120)))
        .expiration(java.util.Date.from(Instant.now().minusSeconds(60)))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();

    mockMvc.perform(get("/api/auth/menu").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value("Unauthorized"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void logoutRevokesTheCurrentTokenForSubsequentRequests() throws Exception {
    String token = jwtService.issue(principal());

    mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    mockMvc.perform(get("/api/auth/menu").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value("Unauthorized"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void revocationStoreFailureReturnsInternalServerErrorInsteadOfUnauthorized() throws Exception {
    String token = jwtService.issue(principal());
    tokenRevocationStore.failReads = true;

    mockMvc.perform(get("/api/auth/menu")
            .header("Authorization", "Bearer " + token)
            .header("X-Request-Id", "req-redis-failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("Internal server error"))
        .andExpect(jsonPath("$.requestId").value("req-redis-failure"));
  }

  @Test
  void roleRevocationInvalidatesAnAlreadyIssuedToken() throws Exception {
    String token = jwtService.issue(principal());
    org.mockito.Mockito.when(accountStateRepository.findActive(7L))
        .thenReturn(new AccountState(7L, null, List.of()));

    mockMvc.perform(get("/api/auth/menu").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));
  }

  private UserPrincipal principal() {
    return new UserPrincipal(7L, "alice", "Alice", null, List.of(Role.DEPT_VIEWER));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RevocationConfig {
    @Bean
    InMemoryTokenRevocationStore tokenRevocationStore() {
      return new InMemoryTokenRevocationStore();
    }
  }

  private static final class InMemoryTokenRevocationStore implements TokenRevocationStore {
    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();
    private boolean failReads;

    @Override
    public void revoke(String jti, java.time.Duration ttl) {
      revokedTokens.put(jti, Instant.now().plus(ttl));
    }

    @Override
    public boolean isRevoked(String jti) {
      if (failReads) {
        throw new IllegalStateException("Redis unavailable");
      }
      Instant expiresAt = revokedTokens.get(jti);
      return expiresAt != null && expiresAt.isAfter(Instant.now());
    }
  }
}
