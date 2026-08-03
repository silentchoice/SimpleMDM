package com.example.mdm.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {
  private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = request.getHeader(RequestId.HEADER);
    if (requestId == null || !VALID_REQUEST_ID.matcher(requestId).matches()) {
      requestId = UUID.randomUUID().toString();
    }
    request.setAttribute(RequestId.ATTRIBUTE, requestId);
    response.setHeader(RequestId.HEADER, requestId);
    filterChain.doFilter(request, response);
  }
}
