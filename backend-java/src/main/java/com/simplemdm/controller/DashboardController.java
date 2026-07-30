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
        return ApiResponse.ok(dashboardService.getStats());
    }

    @GetMapping("/stats")
    public ApiResponse stats() {
        return ApiResponse.ok(dashboardService.getStats());
    }
}
