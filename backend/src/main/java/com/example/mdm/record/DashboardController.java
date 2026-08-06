package com.example.mdm.record;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
  private final DashboardService dashboard;

  public DashboardController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/api/dashboard/summary")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_EDITOR','DEPT_APPROVER','DEPT_VIEWER')")
  public ApiResponse<DashboardService.DashboardSummary> summary(HttpServletRequest request) {
    return ApiResponse.success(dashboard.summary(),
        (String) request.getAttribute(RequestId.ATTRIBUTE));
  }
}
