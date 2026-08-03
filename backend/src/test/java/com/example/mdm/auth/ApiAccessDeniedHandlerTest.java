package com.example.mdm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mdm.common.api.RequestId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class ApiAccessDeniedHandlerTest {

  @Test
  void writesForbiddenAsTheSharedResponseContract() throws Exception {
    var request = new MockHttpServletRequest();
    request.setAttribute(RequestId.ATTRIBUTE, "req-forbidden");
    var response = new MockHttpServletResponse();

    new ApiAccessDeniedHandler(new ObjectMapper()).handle(request, response, new AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(new ObjectMapper().readTree(response.getContentAsByteArray()))
        .isEqualTo(new ObjectMapper().readTree("""
            {"code":403,"message":"Forbidden","data":null,"requestId":"req-forbidden"}
            """));
  }
}
