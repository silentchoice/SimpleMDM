package com.example.mdm.system;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class AdminInitializerTest {
  @Test
  void missingEnvironmentCredentialsDoNotCreateAWeakAdministrator() {
    var repository = org.mockito.Mockito.mock(UserRepository.class);
    var initializer = new AdminInitializer(repository,
        new AdminProperties("", "", ""), org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class));

    initializer.run();

    verify(repository, never()).createInitialAdministrator(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
