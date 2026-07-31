package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.LoginRequest;
import com.simplemdm.model.SysUser;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.AuthService;
import com.simplemdm.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionService permService;

    public AuthController(AuthService authService, PermissionService permService) {
        this.authService = authService;
        this.permService = permService;
    }

    @PostMapping("/login")
    public ApiResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            Map<String, Object> result = authService.login(req.username, req.password);
            return ApiResponse.ok("登录成功", result);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse me() {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        if (user == null) return ApiResponse.error(401, "请先登录");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("real_name", user.getRealName());
        userMap.put("is_admin", user.getIsAdmin());
        userMap.put("department", user.getDepartment());
        userMap.put("status", user.getStatus());

        List<SysUserPermission> perms = permService.getUserPermissions(user.getId());
        List<Map<String, Object>> permList = new ArrayList<>();
        for (SysUserPermission p : perms) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId());
            pm.put("perm_type", p.getPermType());
            pm.put("scope_type", p.getScopeType());
            pm.put("scope_value", p.getScopeValue());
            pm.put("system_code", p.getSystemCode());
            permList.add(pm);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("user", userMap);
        data.put("permissions", permList);
        return ApiResponse.ok(data);
    }
}
