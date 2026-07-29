package com.simplemdm.dto;

import java.util.List;
import java.util.Map;

public class DashboardDTO {
    public long totalPersonnel;
    public long pendingApprovals;
    public double pushSuccessRate;
    public List<Map<String, Object>> recentApprovals;
}
