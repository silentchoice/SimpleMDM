package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    public DashboardController(DashboardService ds) { this.dashboardService = ds; }

    @GetMapping("/stats")
    public ApiResponse stats() {
        return ApiResponse.ok(dashboardService.getStats());
    }
}
