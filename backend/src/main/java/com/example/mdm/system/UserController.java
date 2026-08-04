package com.example.mdm.system;

import com.example.mdm.auth.Role;
import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
  private final UserService service;
  public UserController(UserService service) { this.service = service; }

  @GetMapping public ApiResponse<List<SystemUser>> list(HttpServletRequest request) {
    return ApiResponse.success(service.list(), requestId(request));
  }
  @PostMapping public ApiResponse<SystemUser> create(@Valid @RequestBody CreateUserRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(service.create(body.username(), body.password(), body.displayName(),
        body.departmentId(), body.roles()), requestId(request));
  }
  @PutMapping("/{id}") public ApiResponse<SystemUser> update(@PathVariable long id,
      @Valid @RequestBody UpdateUserRequest body, HttpServletRequest request) {
    return ApiResponse.success(service.update(id, body.displayName(), body.departmentId()), requestId(request));
  }
  @PatchMapping("/{id}/status") public ApiResponse<Void> status(@PathVariable long id,
      @RequestParam EntityStatus status, HttpServletRequest request) {
    service.setStatus(id, status); return ApiResponse.success(null, requestId(request));
  }
  @PutMapping("/{id}/roles") public ApiResponse<Void> roles(@PathVariable long id,
      @RequestBody List<Role> roles, HttpServletRequest request) {
    service.assignRoles(id, roles); return ApiResponse.success(null, requestId(request));
  }
  private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestId.ATTRIBUTE); }
  public record CreateUserRequest(@NotBlank String username, @NotBlank String password,
      @NotBlank String displayName, Long departmentId, @NotNull List<Role> roles) {}
  public record UpdateUserRequest(@NotBlank String displayName, Long departmentId) {}
}
