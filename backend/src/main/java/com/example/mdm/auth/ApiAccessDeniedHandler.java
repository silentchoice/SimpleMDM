package com.example.mdm.auth;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

class ApiAccessDeniedHandler implements AccessDeniedHandler {
  private final ObjectMapper objectMapper;

  ApiAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    Object requestId = request.getAttribute(RequestId.ATTRIBUTE);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
        ApiResponse.failure(403, "Forbidden", requestId == null ? null : requestId.toString()));
  }
}
