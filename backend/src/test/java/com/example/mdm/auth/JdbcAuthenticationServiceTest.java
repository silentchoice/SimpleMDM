package com.example.mdm.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mdm.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class JdbcAuthenticationServiceTest {

  @Test
  void verifiesPasswordHashEvenWhenUsernameIsUnknown() {
    var jdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    var passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    when(jdbcTemplate.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
        .thenReturn(List.of());
    var service = new JdbcAuthenticationService(jdbcTemplate, passwordEncoder);

    assertThatThrownBy(() -> service.authenticate("missing-user", "wrong-password"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Invalid username or password");

    verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("wrong-password"), anyString());
  }
}
