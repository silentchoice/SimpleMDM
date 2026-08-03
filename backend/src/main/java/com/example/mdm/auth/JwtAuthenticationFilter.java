package com.example.mdm.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final TokenRevocationStore tokenRevocationStore;

  JwtAuthenticationFilter(JwtService jwtService, TokenRevocationStore tokenRevocationStore) {
    this.jwtService = jwtService;
    this.tokenRevocationStore = tokenRevocationStore;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      try {
        JwtService.ParsedToken parsedToken = jwtService.parseToken(authorization.substring(7));
        if (tokenRevocationStore.isRevoked(parsedToken.jti())) {
          SecurityContextHolder.clearContext();
          filterChain.doFilter(request, response);
          return;
        }
        UserPrincipal principal = parsedToken.principal();
        var authorities = principal.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, authorities));
      } catch (RuntimeException ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }
}
