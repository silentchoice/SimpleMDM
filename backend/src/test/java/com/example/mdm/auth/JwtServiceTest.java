package com.example.mdm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void parsesThePrincipalIssuedIntoASignedToken() {
    var service = new JwtService(new JwtProperties("01234567890123456789012345678901", 60));
    var principal = new UserPrincipal(9L, "bob", "Bob", new DepartmentPrincipal(5L, "OPS", "Operations"),
        List.of(Role.DEPT_APPROVER));

    var parsed = service.parse(service.issue(principal));

    assertThat(parsed).isEqualTo(principal);
  }
}
