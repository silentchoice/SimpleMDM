package com.example.mdm.auth;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationService authenticationService;
  private final JwtService jwtService;

  public AuthController(AuthenticationService authenticationService, JwtService jwtService) {
    this.authenticationService = authenticationService;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    UserPrincipal principal = authenticationService.authenticate(request.username(), request.password());
    return ApiResponse.success(LoginResponse.from(jwtService.issue(principal), principal), requestId(servletRequest));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(HttpServletRequest request) {
    return ApiResponse.success(null, requestId(request));
  }

  @GetMapping("/menu")
  public ApiResponse<Map<String, List<String>>> menu(Authentication authentication, HttpServletRequest request) {
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    List<String> roles = principal.roles().stream().map(Enum::name).toList();
    return ApiResponse.success(Map.of("roles", roles), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return (String) request.getAttribute(RequestId.ATTRIBUTE);
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }

  public record LoginResponse(String accessToken, LoginUser user, List<String> roles,
                              DepartmentPrincipal department) {
    static LoginResponse from(String accessToken, UserPrincipal principal) {
      return new LoginResponse(accessToken,
          new LoginUser(principal.id(), principal.username(), principal.displayName()),
          principal.roles().stream().map(Enum::name).toList(), principal.department());
    }
  }

  public record LoginUser(long id, String username, String displayName) {
  }
}
