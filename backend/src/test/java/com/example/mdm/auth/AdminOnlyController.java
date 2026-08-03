package com.example.mdm.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AdminOnlyController {
  @GetMapping("/api/test/admin")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  String adminOnly() {
    return "admin";
  }
}
