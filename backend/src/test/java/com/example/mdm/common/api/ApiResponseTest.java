package com.example.mdm.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  void successWrapsDataInTheSharedFourFieldContract() {
    var response = ApiResponse.success(Map.of("username", "alice"), "req-123");

    assertThat(response.code()).isEqualTo(0);
    assertThat(response.message()).isEqualTo("OK");
    assertThat(response.data()).isEqualTo(Map.of("username", "alice"));
    assertThat(response.requestId()).isEqualTo("req-123");
  }
}
