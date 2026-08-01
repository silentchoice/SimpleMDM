package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.LoginRequest;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.system.User;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            return ApiResponse.ok("Login successful", authService.login(request.systemCode, request.username, request.password));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(401, exception.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse me() {
        User user = JwtInterceptor.CURRENT_USER.get();
        if (user == null) {
            return ApiResponse.error(401, "Authentication required");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", authService.userView(user));
        data.put("permissions", authService.permissionViews(user));
        return ApiResponse.ok(data);
    }
}
