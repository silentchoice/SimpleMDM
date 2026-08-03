package com.example.mdm.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey signingKey;
  private final long expirationSeconds;

  public JwtService(JwtProperties properties) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalStateException("JWT_SECRET must be configured");
    }
    if (properties.expirationSeconds() <= 0) {
      throw new IllegalArgumentException("JWT expirationSeconds must be positive");
    }
    this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.expirationSeconds = properties.expirationSeconds();
  }

  public String issue(UserPrincipal principal) {
    Instant now = Instant.now();
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(principal.username())
        .claim("userId", principal.id())
        .claim("displayName", principal.displayName())
        .claim("roles", principal.roles().stream().map(Enum::name).toList())
        .claim("departmentId", principal.department() == null ? null : principal.department().id())
        .claim("departmentCode", principal.department() == null ? null : principal.department().code())
        .claim("departmentName", principal.department() == null ? null : principal.department().name())
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(now.plusSeconds(expirationSeconds)))
        .signWith(signingKey)
        .compact();
  }

  @SuppressWarnings("unchecked")
  public UserPrincipal parse(String token) {
    return parseToken(token).principal();
  }

  @SuppressWarnings("unchecked")
  ParsedToken parseToken(String token) {
    Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    if (claims.getId() == null || claims.getId().isBlank()) {
      throw new IllegalArgumentException("JWT token id is required");
    }
    List<Role> roles = ((List<String>) claims.get("roles", List.class)).stream().map(Role::valueOf).toList();
    Long departmentId = claims.get("departmentId", Long.class);
    DepartmentPrincipal department = departmentId == null ? null : new DepartmentPrincipal(departmentId,
        claims.get("departmentCode", String.class), claims.get("departmentName", String.class));
    return new ParsedToken(new UserPrincipal(claims.get("userId", Long.class), claims.getSubject(),
        claims.get("displayName", String.class), department, roles), claims.getId(), claims.getExpiration().toInstant());
  }

  record ParsedToken(UserPrincipal principal, String jti, Instant expiresAt) {
  }
}
