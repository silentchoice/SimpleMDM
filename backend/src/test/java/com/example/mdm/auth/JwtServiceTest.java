package com.example.mdm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.time.Instant;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void rejectsNonPositiveTokenExpiration() {
    assertThatThrownBy(() -> new JwtService(new JwtProperties("01234567890123456789012345678901", 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("JWT expirationSeconds must be positive");
  }

  @Test
  void parsesThePrincipalIssuedIntoASignedToken() {
    var service = new JwtService(new JwtProperties("01234567890123456789012345678901", 60));
    var principal = new UserPrincipal(9L, "bob", "Bob", new DepartmentPrincipal(5L, "OPS", "Operations"),
        List.of(Role.DEPT_APPROVER));

    var parsed = service.parse(service.issue(principal));

    assertThat(parsed).isEqualTo(principal);
  }

  @Test
  void issuesAUniqueTokenIdForEachToken() {
    var service = new JwtService(new JwtProperties("01234567890123456789012345678901", 60));
    var principal = new UserPrincipal(9L, "bob", "Bob", null, List.of(Role.DEPT_APPROVER));

    var first = service.parseToken(service.issue(principal));
    var second = service.parseToken(service.issue(principal));

    assertThat(first.jti()).isNotBlank().isNotEqualTo(second.jti());
  }

  @Test
  void rejectsTokenWithoutTokenId() {
    var service = new JwtService(new JwtProperties("01234567890123456789012345678901", 60));
    String token = Jwts.builder()
        .subject("bob")
        .claim("userId", 9L)
        .claim("displayName", "Bob")
        .claim("roles", List.of("DEPT_APPROVER"))
        .expiration(java.util.Date.from(Instant.now().plusSeconds(60)))
        .signWith(Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)))
        .compact();

    assertThatThrownBy(() -> service.parseToken(token))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("JWT token id is required");
  }
}
