package com.simplemdm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMinutes * 60 * 1000;
    }

    public String createToken(Long userId) {
        return createToken(userId, null);
    }

    public String createToken(Long userId, Long systemId) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .claim("user_id", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key);
        if (systemId != null) {
            builder.claim("system_id", systemId);
        }
        return builder.compact();
    }

    public Long getUserIdFromToken(String token) {
        return claim(token, "user_id");
    }

    public Long getSystemIdFromToken(String token) {
        return claim(token, "system_id");
    }

    private Long claim(String token, String claimName) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get(claimName, Long.class);
        } catch (Exception e) {
            return null;
        }
    }
}
