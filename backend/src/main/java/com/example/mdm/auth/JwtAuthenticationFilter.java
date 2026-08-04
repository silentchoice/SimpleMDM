package com.example.mdm.auth;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private final JwtService jwtService;
  private final TokenRevocationStore tokenRevocationStore;
  private final ObjectMapper objectMapper;
  private final AccountStateRepository accountStates;

  JwtAuthenticationFilter(JwtService jwtService, TokenRevocationStore tokenRevocationStore,
      ObjectMapper objectMapper, AccountStateRepository accountStates) {
    this.jwtService = jwtService;
    this.tokenRevocationStore = tokenRevocationStore;
    this.objectMapper = objectMapper;
    this.accountStates = accountStates;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      JwtService.ParsedToken parsedToken;
      try {
        parsedToken = jwtService.parseToken(authorization.substring(7));
      } catch (RuntimeException ignored) {
        SecurityContextHolder.clearContext();
        filterChain.doFilter(request, response);
        return;
      }
      try {
        if (tokenRevocationStore.isRevoked(parsedToken.jti())) {
          SecurityContextHolder.clearContext();
          filterChain.doFilter(request, response);
          return;
        }
        UserPrincipal tokenPrincipal = parsedToken.principal();
        AccountState current = accountStates.findActive(tokenPrincipal.id());
        Long tokenDepartment = tokenPrincipal.department() == null ? null : tokenPrincipal.department().id();
        if (current == null || !Objects.equals(current.departmentId(), tokenDepartment)
            || !new HashSet<>(current.roles()).equals(new HashSet<>(tokenPrincipal.roles()))) {
          SecurityContextHolder.clearContext();
          filterChain.doFilter(request, response);
          return;
        }
      } catch (RuntimeException exception) {
        Object requestId = request.getAttribute(RequestId.ATTRIBUTE);
        log.error("Authentication state lookup failed requestId={} exceptionType={}", requestId,
            exception.getClass().getName());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
            ApiResponse.failure(500, "Internal server error",
                requestId == null ? null : requestId.toString()));
        return;
      }
      UserPrincipal principal = parsedToken.principal();
      var authorities = principal.roles().stream()
          .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
    filterChain.doFilter(request, response);
  }
}
