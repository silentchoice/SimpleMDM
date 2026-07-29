package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.SysUser;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    public DashboardController(DashboardService ds) { this.dashboardService = ds; }

    @GetMapping("")
    public ApiResponse dashboard() {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        return ApiResponse.ok(dashboardService.getStats());
    }

    @GetMapping("/stats")
    public ApiResponse stats() {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        return ApiResponse.ok(dashboardService.getStats());
    }
}
