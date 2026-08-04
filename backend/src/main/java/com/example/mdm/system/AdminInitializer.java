package com.example.mdm.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {
  private final UserRepository repository;
  private final AdminProperties properties;
  private final PasswordEncoder passwordEncoder;

  public AdminInitializer(UserRepository repository, AdminProperties properties, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.properties = properties;
    this.passwordEncoder = passwordEncoder;
  }

  @Override public void run(ApplicationArguments args) { run(); }

  public void run() {
    if (blank(properties.username()) || blank(properties.password())) return;
    String displayName = blank(properties.displayName()) ? properties.username() : properties.displayName();
    repository.createInitialAdministrator(properties.username(), passwordEncoder.encode(properties.password()), displayName);
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
}
