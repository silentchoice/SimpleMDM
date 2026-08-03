package com.example.mdm.auth;

public interface AuthenticationService {
  UserPrincipal authenticate(String username, String password);
}
