package com.example.mdm.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  @Test
  void replacesAnInvalidCallerSuppliedRequestId() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(RequestId.HEADER, "not trusted");
    var response = new MockHttpServletResponse();

    new RequestIdFilter().doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestId.HEADER)).matches("[A-Za-z0-9._-]{1,128}")
        .isNotEqualTo("not trusted");
    assertThat(request.getAttribute(RequestId.ATTRIBUTE)).isEqualTo(response.getHeader(RequestId.HEADER));
  }
}
