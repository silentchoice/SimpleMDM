package com.example.mdm.system;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role")
public class RoleController {
  private final AuthorizationService authorization;
  public RoleController(AuthorizationService authorization) { this.authorization = authorization; }
  @GetMapping public ApiResponse<List<Role>> list(HttpServletRequest request) {
    authorization.requireRole(Role.SUPER_ADMIN);
    return ApiResponse.success(List.of(Role.values()), (String) request.getAttribute(RequestId.ATTRIBUTE));
  }
}
